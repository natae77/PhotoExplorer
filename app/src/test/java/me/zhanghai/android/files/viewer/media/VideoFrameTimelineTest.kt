/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoFrameTimelineTest {
    @Test
    fun adjacentIndex_usesActualVfrPtsAndSkipsExactCurrentFrame() {
        val timeline = timeline(0, 40, 120, 200, 450, 500, 850, 1_200)

        assertEquals(4, timeline.adjacentIndex(500, -1))
        assertEquals(6, timeline.adjacentIndex(500, 1))
        assertEquals(3, timeline.adjacentIndex(430, -1))
        assertEquals(4, timeline.adjacentIndex(430, 1))
    }

    @Test
    fun cursorCanWalkPresentationOrderInBothDirections() {
        val timeline = timeline(0, 33, 67, 100)

        val firstNext = timeline.adjacentIndex(50, 1)!!
        assertEquals(2, firstNext)
        assertEquals(100L, timeline.seekPositionMs(firstNext + 1))
        assertEquals(33L, timeline.seekPositionMs(firstNext - 1))
    }

    @Test
    fun seekPosition_accountsForNonZeroPtsOrigin() {
        val timeline = VideoFrameTimeline(longArrayOf(20_000, 52_000, 85_000))

        assertEquals(0L, timeline.seekPositionMs(0))
        assertEquals(32L, timeline.seekPositionMs(1))
        assertEquals(65L, timeline.seekPositionMs(2))
    }

    @Test
    fun seekPosition_rejectsSamplesThatMillisecondClockCannotDistinguish() {
        val timeline = VideoFrameTimeline(longArrayOf(0, 400, 800))

        assertEquals(0L, timeline.seekPositionMs(0))
        assertNull(timeline.seekPositionMs(1))
        assertEquals(1L, timeline.seekPositionMs(2))
    }

    private fun timeline(vararg milliseconds: Long) =
        VideoFrameTimeline(LongArray(milliseconds.size) { milliseconds[it] * 1_000L })
}
