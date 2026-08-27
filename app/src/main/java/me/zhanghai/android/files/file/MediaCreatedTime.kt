/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import androidx.annotation.WorkerThread
import androidx.exifinterface.media.ExifInterface
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import me.zhanghai.android.files.fileproperties.date
import me.zhanghai.android.files.fileproperties.image.inferDateTimeOriginal
import me.zhanghai.android.files.filelist.isRemotePath
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.ftp.isFtpPath
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.isMediaMetadataRetrieverCompatible
import me.zhanghai.android.files.util.setDataSource
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.files.BuildConfig
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Resolves the "media created time" of a file, see PersonalDev/Doc/08-media-view-mode-spec.md 5.
 *
 * The rule is `min(metadata timestamp, last modified time)`: shooting a photo cannot happen after
 * the file was last written, so whichever of the two is later is assumed to be a timestamp that got
 * overwritten while moving the file around.
 */
object MediaCreatedTime {
    private const val LOG_TAG = "MediaCreatedTime"

    // LruCache cannot store null values, and "we looked and there is nothing" is exactly the answer
    // that is most expensive to recompute (it means the MediaMetadataRetriever fallback ran). So a
    // sentinel is stored instead. See plan 1.3.
    private const val NO_VALUE = Long.MIN_VALUE

    private const val CACHE_SIZE = 4096

    private val cache = LruCache<CacheKey, Long>(CACHE_SIZE)

    /**
     * Returns the media created time in milliseconds since the Unix epoch, or null when this file
     * has no usable one (not media, excluded path, or nothing could be read).
     *
     * Callers fall back to the last modified time when this returns null.
     */
    @WorkerThread
    fun read(path: Path, attributes: BasicFileAttributes, mimeType: MimeType): Long? {
        if (!isSupported(path, attributes, mimeType)) {
            return null
        }
        val lastModifiedMillis = attributes.lastModifiedTime().toMillis()
        val key = CacheKey(path.toString(), attributes.size(), lastModifiedMillis)
        cache[key]?.let { return if (it == NO_VALUE) null else it }
        val metadataMillis = readMetadataMillis(path, mimeType, lastModifiedMillis)
        // min(metadata, mtime), see spec 5.4.
        val value = metadataMillis?.coerceAtMost(lastModifiedMillis)
        cache.put(key, value ?: NO_VALUE)
        if (BuildConfig.DEBUG) {
            Log.d(
                LOG_TAG,
                "${path.fileName} meta=${format(metadataMillis)}" +
                    " mtime=${format(lastModifiedMillis)} -> ${format(value)}"
            )
        }
        return value
    }

    /** Mirrors the exclusions of `FileItem.supportsThumbnail`, see plan 1.2. */
    private fun isSupported(
        path: Path,
        attributes: BasicFileAttributes,
        mimeType: MimeType
    ): Boolean {
        if (attributes.isDirectory) {
            return false
        }
        if (!mimeType.isImage && !mimeType.isVideo) {
            return false
        }
        if (path.isArchivePath) {
            return false
        }
        if (path.isRemotePath) {
            // FTP is excluded unconditionally, same as thumbnail loading does.
            if (path.isFtpPath || !Settings.READ_REMOTE_FILES_FOR_THUMBNAIL.valueCompat) {
                return false
            }
        }
        return true
    }

    private fun readMetadataMillis(path: Path, mimeType: MimeType, lastModifiedMillis: Long): Long? {
        return when {
            mimeType.isImage -> readExifMillis(path, lastModifiedMillis)
            mimeType.isVideo ->
                Mp4CreationTime.read(path) ?: readMediaMetadataRetrieverMillis(path)
            else -> null
        }
    }

    private fun readExifMillis(path: Path, lastModifiedMillis: Long): Long? =
        try {
            path.newInputStream().use {
                ExifInterface(it)
                    .inferDateTimeOriginal(Instant.ofEpochMilli(lastModifiedMillis))
                    ?.toEpochMilli()
            }
        } catch (e: Exception) {
            null
        }

    /** Fallback for containers the lightweight box parser cannot handle (mkv, webm, ...). */
    private fun readMediaMetadataRetrieverMillis(path: Path): Long? {
        if (!path.isMediaMetadataRetrieverCompatible) {
            return null
        }
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(path)
                retriever.date?.toEpochMilli()
            }
        } catch (e: Exception) {
            null
        }
    }

    private inline fun <R> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> R): R =
        try {
            block(this)
        } finally {
            try {
                release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    private fun format(millis: Long?): String =
        if (millis == null) {
            "null"
        } else {
            DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))
        }

    private data class CacheKey(
        val path: String,
        val size: Long,
        val lastModifiedMillis: Long
    )
}
