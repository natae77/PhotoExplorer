/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.media3.common.Format
import java8.nio.file.Path
import me.zhanghai.android.files.file.fileProviderUri

data class VideoTrackIdentity(
    val mimeType: String?,
    val width: Int,
    val height: Int
) {
    companion object {
        fun from(format: Format?): VideoTrackIdentity? = format?.let {
            VideoTrackIdentity(it.sampleMimeType, it.width, it.height)
        }
    }
}

class VideoFrameTimelineUnavailableException(message: String) : Exception(message)

object VideoFrameTimelineReader {
    const val MAX_TIMELINE_BYTES = 16L * 1024L * 1024L
    const val MAX_SAMPLE_COUNT = 2_000_000

    fun read(
        context: Context,
        path: Path,
        selectedTrack: VideoTrackIdentity?
    ): VideoFrameTimeline {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, path.fileProviderUri, null)
            val candidates = (0 until extractor.trackCount).filter { index ->
                extractor.getTrackFormat(index).mimeType?.startsWith("video/") == true
            }
            if (candidates.isEmpty()) {
                throw VideoFrameTimelineUnavailableException("No video track")
            }
            val matching = selectedTrack?.let { identity ->
                candidates.filter { extractor.getTrackFormat(it).matches(identity) }
            }.orEmpty()
            val trackIndex = when {
                candidates.size == 1 -> candidates.single()
                matching.size == 1 -> matching.single()
                else -> throw VideoFrameTimelineUnavailableException("Ambiguous video track")
            }
            extractor.selectTrack(trackIndex)
            val times = LongArrayBuilder()
            while (true) {
                val sampleTime = extractor.sampleTime
                // MediaExtractor uses exactly -1 for end-of-stream. MOV edit lists may expose
                // legitimate negative PTS values, which are normalized by VideoFrameTimeline.
                if (sampleTime == -1L) break
                if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
                    throw VideoFrameTimelineUnavailableException("Partial-frame samples")
                }
                times.add(sampleTime)
                if (times.size > MAX_SAMPLE_COUNT || times.size.toLong() * Long.SIZE_BYTES > MAX_TIMELINE_BYTES) {
                    throw VideoFrameTimelineUnavailableException("Frame timeline is too large")
                }
                if (!extractor.advance()) break
            }
            val sorted = times.toArray().apply { sort() }
            val unique = sorted.deduplicateSorted()
            if (unique.isEmpty()) {
                throw VideoFrameTimelineUnavailableException("No complete video samples")
            }
            return VideoFrameTimeline(unique)
        } catch (e: VideoFrameTimelineUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw VideoFrameTimelineUnavailableException(e.message ?: e.javaClass.simpleName)
        } finally {
            extractor.release()
        }
    }

    private val MediaFormat.mimeType: String?
        get() = getString(MediaFormat.KEY_MIME)

    private fun MediaFormat.matches(identity: VideoTrackIdentity): Boolean =
        mimeType == identity.mimeType
            && intOrUnset(MediaFormat.KEY_WIDTH) == identity.width
            && intOrUnset(MediaFormat.KEY_HEIGHT) == identity.height

    private fun MediaFormat.intOrUnset(key: String): Int = if (containsKey(key)) getInteger(key) else -1

    private class LongArrayBuilder {
        private var values = LongArray(1_024)
        var size = 0
            private set

        fun add(value: Long) {
            if (size == values.size) values = values.copyOf(values.size * 2)
            values[size++] = value
        }

        fun toArray(): LongArray = values.copyOf(size)
    }

    private fun LongArray.deduplicateSorted(): LongArray {
        if (isEmpty()) return this
        var outputSize = 1
        for (index in 1..lastIndex) {
            if (this[index] != this[outputSize - 1]) this[outputSize++] = this[index]
        }
        return copyOf(outputSize)
    }
}
