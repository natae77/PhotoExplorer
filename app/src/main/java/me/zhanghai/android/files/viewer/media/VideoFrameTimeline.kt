/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

/** Presentation timestamps of the actual video samples, in ascending presentation order. */
data class VideoFrameTimeline(
    val presentationTimesUs: LongArray,
    val originUs: Long = presentationTimesUs.firstOrNull() ?: 0L
) {
    init {
        require(presentationTimesUs.isNotEmpty())
        for (index in 1 until presentationTimesUs.size) {
            require(presentationTimesUs[index - 1] < presentationTimesUs[index])
        }
    }

    val byteSize: Long
        get() = presentationTimesUs.size.toLong() * Long.SIZE_BYTES

    /** Finds the actual sample before/after [playerPositionMs], never the current sample itself. */
    fun adjacentIndex(playerPositionMs: Long, direction: Int): Int? {
        require(direction == -1 || direction == 1)
        val absolutePositionUs = originUs + playerPositionMs.coerceAtLeast(0L) * 1_000L
        val insertion = presentationTimesUs.binarySearch(absolutePositionUs)
        val index = if (direction < 0) {
            if (insertion >= 0) insertion - 1 else -insertion - 2
        } else {
            if (insertion >= 0) insertion + 1 else -insertion - 1
        }
        return index.takeIf { it in presentationTimesUs.indices }
    }

    /**
     * Converts a sample PTS to Media3's millisecond seek clock.
     *
     * Rounding up is safe only while the rounded time is still before the following sample. If
     * two samples cannot be represented independently by the millisecond API, that target is
     * deliberately unavailable instead of pretending that the wrong frame is exact.
     */
    fun seekPositionMs(index: Int): Long? {
        if (index !in presentationTimesUs.indices) return null
        val relativeUs = presentationTimesUs[index] - originUs
        val positionMs = if (relativeUs <= 0L) 0L else (relativeUs + 999L) / 1_000L
        val nextRelativeUs = presentationTimesUs.getOrNull(index + 1)?.minus(originUs)
        if (nextRelativeUs != null && positionMs * 1_000L >= nextRelativeUs) return null
        return positionMs
    }

    override fun equals(other: Any?): Boolean =
        other is VideoFrameTimeline
            && originUs == other.originUs
            && presentationTimesUs.contentEquals(other.presentationTimesUs)

    override fun hashCode(): Int = 31 * presentationTimesUs.contentHashCode() + originUs.hashCode()
}

enum class VideoSeekUnit { FRAME, SECOND }

data class VideoFrameCursor(val path: java8.nio.file.Path, val index: Int, val generation: Long)
