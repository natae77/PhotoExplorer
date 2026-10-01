/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.util.AtomicFile
import java8.nio.file.Path
import me.zhanghai.android.files.app.application
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32

internal data class MediaCreatedTimeCacheEntry(
    val fileName: String,
    val size: Long,
    val lastModifiedMillis: Long,
    val createdTimeMillis: Long?
)

internal data class MediaCreatedTimeCacheSnapshot(
    val folderKey: String,
    val entries: Map<String, MediaCreatedTimeCacheEntry>
)

internal object MediaCreatedTimeCache {
    private const val MAGIC = 0x50454D4354433031L // "PEMCTC01"
    private const val SCHEMA_VERSION = 1
    private const val EXTRACTOR_VERSION = 1
    internal const val MAX_FILE_SIZE = 16 * 1024 * 1024L
    // magic + schema version + extractor version + payload size + CRC32
    internal const val FILE_OVERHEAD_SIZE = Long.SIZE_BYTES + 3 * Int.SIZE_BYTES + Int.SIZE_BYTES
    internal const val MAX_PAYLOAD_SIZE = MAX_FILE_SIZE - FILE_OVERHEAD_SIZE
    private const val MAX_ENTRY_COUNT = 200_000
    private const val MAX_NAME_BYTE_COUNT = 16 * 1024

    private val directory: File by lazy {
        File(application.noBackupFilesDir, "media_created_time_cache").apply { mkdirs() }
    }

    fun folderKey(path: Path): String {
        val identity = try {
            path.toAbsolutePath().normalize().toUri().toString()
        } catch (exception: Exception) {
            path.fileSystem.provider().scheme + ":" + path.toAbsolutePath().normalize()
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun read(folderKey: String): MediaCreatedTimeCacheSnapshot {
        val atomicFile = AtomicFile(cacheFile(folderKey))
        return try {
            // Do not inspect the base file before openRead(). AtomicFile may need to restore its
            // backup first after an interrupted write.
            val bytes = atomicFile.openRead().use { it.readBytesAtMost(MAX_FILE_SIZE) }
            decode(folderKey, bytes)
        } catch (exception: FileNotFoundException) {
            // A missing cache is normal and is deliberately kept distinct from a corrupt cache.
            MediaCreatedTimeCacheSnapshot(folderKey, emptyMap())
        } catch (exception: Exception) {
            exception.printStackTrace()
            // Do not leave a corrupt base/backup pair behind to fail on every folder visit.
            atomicFile.delete()
            MediaCreatedTimeCacheSnapshot(folderKey, emptyMap())
        }
    }

    @Throws(IOException::class)
    fun write(snapshot: MediaCreatedTimeCacheSnapshot) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Cannot create media created time cache directory")
        }
        val bytes = encode(snapshot)
        val atomicFile = AtomicFile(cacheFile(snapshot.folderKey))
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (exception: Exception) {
            atomicFile.failWrite(output)
            throw exception
        }
        trim()
    }

    internal fun encode(snapshot: MediaCreatedTimeCacheSnapshot): ByteArray {
        require(snapshot.entries.size <= MAX_ENTRY_COUNT)
        val encodedNames = snapshot.entries.values.sortedBy { it.fileName }.map { entry ->
            entry to entry.fileName.toByteArray(Charsets.UTF_8).also {
                require(it.size <= MAX_NAME_BYTE_COUNT)
            }
        }
        val payloadSize = encodedNames.fold(Int.SIZE_BYTES.toLong()) { size, (entry, nameBytes) ->
            val valueSize = if (entry.createdTimeMillis != null) Long.SIZE_BYTES else 0
            size + Int.SIZE_BYTES + nameBytes.size + 2 * Long.SIZE_BYTES + 1 + valueSize
        }
        requirePayloadSizeWithinLimit(payloadSize)
        val payloadBytes = ByteArrayOutputStream(payloadSize.toInt()).use { byteOutput ->
            DataOutputStream(byteOutput).use { output ->
                output.writeInt(snapshot.entries.size)
                for ((entry, nameBytes) in encodedNames) {
                    output.writeInt(nameBytes.size)
                    output.write(nameBytes)
                    output.writeLong(entry.size)
                    output.writeLong(entry.lastModifiedMillis)
                    output.writeBoolean(entry.createdTimeMillis != null)
                    entry.createdTimeMillis?.let { output.writeLong(it) }
                }
            }
            byteOutput.toByteArray()
        }
        val crc = CRC32().apply { update(payloadBytes) }.value
        return ByteArrayOutputStream().use { byteOutput ->
            DataOutputStream(byteOutput).use { output ->
                output.writeLong(MAGIC)
                output.writeInt(SCHEMA_VERSION)
                output.writeInt(EXTRACTOR_VERSION)
                output.writeInt(payloadBytes.size)
                output.write(payloadBytes)
                output.writeInt(crc.toInt())
            }
            byteOutput.toByteArray()
        }
    }

    internal fun decode(folderKey: String, bytes: ByteArray): MediaCreatedTimeCacheSnapshot {
        require(bytes.size <= MAX_FILE_SIZE)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readLong() == MAGIC)
            require(input.readInt() == SCHEMA_VERSION)
            require(input.readInt() == EXTRACTOR_VERSION)
            val payloadSize = input.readInt()
            require(payloadSize >= Int.SIZE_BYTES && payloadSize <= MAX_PAYLOAD_SIZE)
            val payload = ByteArray(payloadSize)
            input.readFully(payload)
            val expectedCrc = input.readInt().toLong() and 0xFFFFFFFFL
            require(input.read() == -1)
            require(CRC32().apply { update(payload) }.value == expectedCrc)
            DataInputStream(ByteArrayInputStream(payload)).use { payloadInput ->
                val entryCount = payloadInput.readInt()
                require(entryCount in 0..MAX_ENTRY_COUNT)
                val entries = LinkedHashMap<String, MediaCreatedTimeCacheEntry>(entryCount)
                repeat(entryCount) {
                    val nameSize = payloadInput.readInt()
                    require(nameSize in 0..MAX_NAME_BYTE_COUNT)
                    val nameBytes = ByteArray(nameSize)
                    payloadInput.readFully(nameBytes)
                    val name = nameBytes.toString(Charsets.UTF_8)
                    require(name !in entries)
                    val size = payloadInput.readLong()
                    val lastModifiedMillis = payloadInput.readLong()
                    val hasValue = payloadInput.readBoolean()
                    val createdTimeMillis = if (hasValue) payloadInput.readLong() else null
                    if (createdTimeMillis != null) {
                        require(createdTimeMillis >= 0)
                    }
                    entries[name] = MediaCreatedTimeCacheEntry(
                        name, size, lastModifiedMillis, createdTimeMillis
                    )
                }
                require(payloadInput.read() == -1)
                return MediaCreatedTimeCacheSnapshot(folderKey, entries)
            }
        }
    }

    private fun cacheFile(folderKey: String): File = File(directory, "$folderKey.mctc")

    internal fun requirePayloadSizeWithinLimit(payloadSize: Long) {
        require(payloadSize in Int.SIZE_BYTES.toLong()..MAX_PAYLOAD_SIZE)
    }

    private fun InputStream.readBytesAtMost(maxSize: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalSize = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) {
                return output.toByteArray()
            }
            totalSize += count
            require(totalSize <= maxSize)
            output.write(buffer, 0, count)
        }
    }

    private fun trim() {
        val files = directory.listFiles { file -> file.extension == "mctc" } ?: return
        var totalSize = files.sumOf { it.length() }
        if (files.size <= MAX_FILE_COUNT && totalSize <= MAX_TOTAL_SIZE) {
            return
        }
        var count = files.size
        for (file in files.sortedBy { it.lastModified() }) {
            if (count <= TRIM_FILE_COUNT && totalSize <= TRIM_TOTAL_SIZE) {
                break
            }
            val length = file.length()
            if (file.delete()) {
                --count
                totalSize -= length
            }
        }
    }

    private const val MAX_FILE_COUNT = 512
    private const val TRIM_FILE_COUNT = 384
    private const val MAX_TOTAL_SIZE = 64L * 1024 * 1024
    private const val TRIM_TOTAL_SIZE = 48L * 1024 * 1024
}
