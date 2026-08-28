/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.media.MediaMetadataRetriever
import androidx.annotation.WorkerThread
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import me.zhanghai.android.files.compat.use
import me.zhanghai.android.files.file.MediaCreatedTime
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.guessFromPath
import me.zhanghai.android.files.fileproperties.location
import me.zhanghai.android.files.provider.common.readAttributes
import me.zhanghai.android.files.util.setDataSource

/**
 * What the details sheet shows, see spec 11 section 7.1.
 *
 * Every field is nullable: the format ones are unknown until playback is ready, and the metadata
 * ones may simply not be in the file.
 *
 * Not Parcelable on purpose: this is handed to the sheet through a listener, not through
 * arguments, because it changes while the sheet is open. See plan 12 7.3.1.
 */
class VideoDetails(
    val fileName: String,
    val path: String,
    val sizeBytes: Long,
    val createdTimeMillis: Long?,
    val width: Int?,
    val height: Int?,
    val durationMillis: Long?,
    val frameRate: Float?,
    val codec: String?,
    val bitRate: Int?,
    val rotationDegrees: Int?,
    val location: Pair<Float, Float>?
)

/** The half of [VideoDetails] that needs the file system. */
class VideoFileDetails(
    val sizeBytes: Long,
    val createdTimeMillis: Long?,
    val location: Pair<Float, Float>?
)

/** Reads the file half. Call from `Dispatchers.IO`. */
@WorkerThread
fun readVideoFileDetails(path: Path): VideoFileDetails {
    val attributes = path.readAttributes(BasicFileAttributes::class.java)
    val mimeType = MimeType.guessFromPath(path.toString())
    // Spec 11 section 7.2: the same rule the date tiles use, so the two never disagree.
    val createdTimeMillis = MediaCreatedTime.read(path, attributes, mimeType)
    val location = try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(path)
            retriever.location
        }
    } catch (e: Exception) {
        // Observed on the emulator: this fails while ExoPlayer has the same file open, so the
        // location row simply stays away. Everything else in the sheet is unaffected.
        e.printStackTrace()
        null
    }
    return VideoFileDetails(attributes.size(), createdTimeMillis, location)
}

/** Combines the file half with what the player already knows. */
fun buildVideoDetails(
    path: Path,
    fileDetails: VideoFileDetails?,
    format: Format?,
    durationMillis: Long?
): VideoDetails =
    VideoDetails(
        fileName = path.fileName.toString(),
        path = path.toString(),
        sizeBytes = fileDetails?.sizeBytes ?: -1L,
        createdTimeMillis = fileDetails?.createdTimeMillis,
        width = format?.width?.orNullIfNoValue(),
        height = format?.height?.orNullIfNoValue(),
        durationMillis = durationMillis?.takeIf { it != C.TIME_UNSET && it > 0 },
        frameRate = format?.frameRate?.orNullIfNoValue(),
        codec = codecDisplayName(format?.sampleMimeType),
        bitRate = format?.averageBitrate?.orNullIfNoValue()
            ?: format?.peakBitrate?.orNullIfNoValue(),
        rotationDegrees = format?.rotationDegrees?.orNullIfNoValue()?.takeIf { it != 0 },
        location = fileDetails?.location
    )

/** [Format] says "unknown" with [Format.NO_VALUE], not with null. */
private fun Int.orNullIfNoValue(): Int? = if (this == Format.NO_VALUE) null else this

private fun Float.orNullIfNoValue(): Float? =
    if (this == Format.NO_VALUE.toFloat() || isNaN()) null else this

/** `video/hevc` is not what spec 11 section 7.1 wants to show. */
private fun codecDisplayName(sampleMimeType: String?): String? =
    when (sampleMimeType) {
        null -> null
        MimeTypes.VIDEO_H265 -> "HEVC (H.265)"
        MimeTypes.VIDEO_H264 -> "H.264 (AVC)"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_VP9 -> "VP9"
        MimeTypes.VIDEO_MPEG -> "MPEG"
        else -> sampleMimeType
    }
