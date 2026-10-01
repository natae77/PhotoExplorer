/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import java8.nio.file.Path
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

internal data class MediaCreatedTimeEnrichment(
    val files: List<FileItem>,
    internal val snapshot: MediaCreatedTimeCacheSnapshot?,
    internal val snapshotVersion: Long
)

internal object MediaCreatedTimeRepository {
    private const val MAX_MEMORY_FOLDER_COUNT = 8
    private const val MAX_MEMORY_ENTRY_COUNT = 40_000

    private val locks = ConcurrentHashMap<String, Any>()
    private val snapshotVersions = SnapshotVersionTracker()
    private val writer = Executors.newSingleThreadExecutor()
    private val memoryLock = Any()
    private val memory = LinkedHashMap<String, MediaCreatedTimeCacheSnapshot>(16, 0.75f, true)
    private var memoryEntryCount = 0

    fun enrich(
        directory: Path,
        files: List<FileItem>,
        isCurrent: () -> Boolean = { true }
    ): MediaCreatedTimeEnrichment {
        val folderKey = MediaCreatedTimeCache.folderKey(directory)
        return synchronized(lockFor(folderKey)) {
            checkCurrent(isCurrent)
            val oldSnapshot = getSnapshot(folderKey)
            val newEntries = LinkedHashMap<String, MediaCreatedTimeCacheEntry>()
            var isChanged = false
            val enrichedFiles = ArrayList<FileItem>(files.size)
            for (file in files) {
                checkCurrent(isCurrent)
                val attributes = file.attributes
                val entryKey = entryKey(file.path)
                val size = attributes.size()
                val lastModifiedMillis = attributes.lastModifiedTime().toMillis()
                val hasStableValidators = size > 0L && lastModifiedMillis > 0L
                    && (file.mimeType.isImage || file.mimeType.isVideo)
                val cached = oldSnapshot.entries[entryKey]?.takeIf {
                    hasStableValidators
                        && it.size == size && it.lastModifiedMillis == lastModifiedMillis
                }
                val readResult = when {
                    cached != null -> MediaCreatedTime.ReadResult(
                        cached.createdTimeMillis,
                        true
                    )
                    file.mimeType.isImage || file.mimeType.isVideo ->
                        MediaCreatedTime.readForCache(file.path, attributes, file.mimeType)
                    else -> MediaCreatedTime.ReadResult(null, false)
                }
                val createdTimeMillis = readResult.value
                val canPersist = hasStableValidators && readResult.canPersist
                if (canPersist) {
                    val entry = MediaCreatedTimeCacheEntry(
                        entryKey, size, lastModifiedMillis, createdTimeMillis
                    )
                    newEntries[entryKey] = entry
                    isChanged = isChanged || entry != cached
                }
                enrichedFiles += if (createdTimeMillis == file.mediaCreatedTimeMillis) {
                    file
                } else {
                    file.copy(mediaCreatedTimeMillis = createdTimeMillis)
                }
            }
            checkCurrent(isCurrent)
            isChanged = isChanged || oldSnapshot.entries.keys != newEntries.keys
            val snapshot = MediaCreatedTimeCacheSnapshot(folderKey, newEntries)
            putMemory(snapshot)
            val changedSnapshot = snapshot.takeIf { isChanged }
            val snapshotVersion = if (changedSnapshot != null) {
                snapshotVersions.next(folderKey)
            } else {
                snapshotVersions.current(folderKey)
            }
            MediaCreatedTimeEnrichment(enrichedFiles, changedSnapshot, snapshotVersion)
        }
    }

    fun persist(enrichment: MediaCreatedTimeEnrichment) {
        val snapshot = enrichment.snapshot ?: return
        writer.execute {
            if (!snapshotVersions.isCurrent(snapshot.folderKey, enrichment.snapshotVersion)) {
                return@execute
            }
            synchronized(lockFor(snapshot.folderKey)) {
                if (snapshotVersions.isCurrent(snapshot.folderKey, enrichment.snapshotVersion)) {
                    try {
                        MediaCreatedTimeCache.write(snapshot)
                    } catch (exception: Exception) {
                        exception.printStackTrace()
                    }
                }
            }
        }
    }

    private fun getSnapshot(folderKey: String): MediaCreatedTimeCacheSnapshot {
        synchronized(memoryLock) {
            memory[folderKey]?.let { return it }
        }
        return synchronized(lockFor(folderKey)) {
            MediaCreatedTimeCache.read(folderKey).also { putMemory(it) }
        }
    }

    private fun putMemory(snapshot: MediaCreatedTimeCacheSnapshot) {
        if (snapshot.entries.size > MAX_MEMORY_ENTRY_COUNT) {
            return
        }
        synchronized(memoryLock) {
            memory.remove(snapshot.folderKey)?.let { memoryEntryCount -= it.entries.size }
            memory[snapshot.folderKey] = snapshot
            memoryEntryCount += snapshot.entries.size
            while (memory.size > MAX_MEMORY_FOLDER_COUNT
                || memoryEntryCount > MAX_MEMORY_ENTRY_COUNT) {
                val iterator = memory.entries.iterator()
                val removed = iterator.next().value
                iterator.remove()
                memoryEntryCount -= removed.entries.size
            }
        }
    }

    private fun lockFor(folderKey: String): Any = locks.getOrPut(folderKey) { Any() }

    private fun entryKey(path: Path): String =
        try {
            path.toAbsolutePath().normalize().toUri().toString()
        } catch (exception: Exception) {
            path.fileSystem.provider().scheme + ":" + path.toString()
        }

    private fun checkCurrent(isCurrent: () -> Boolean) {
        if (Thread.currentThread().isInterrupted || !isCurrent()) {
            throw InterruptedException()
        }
    }

    internal class SnapshotVersionTracker {
        private val versions = ConcurrentHashMap<String, AtomicLong>()

        fun next(folderKey: String): Long =
            versions.getOrPut(folderKey) { AtomicLong() }.incrementAndGet()

        fun current(folderKey: String): Long = versions[folderKey]?.get() ?: 0L

        fun isCurrent(folderKey: String, version: Long): Boolean =
            current(folderKey) == version
    }
}
