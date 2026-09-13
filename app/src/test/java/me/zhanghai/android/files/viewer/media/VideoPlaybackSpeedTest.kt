/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlaybackSpeedTest {
    @Test
    fun supportedValueIsRestored() {
        assertEquals(0.04f, parsePersistedVideoPlaybackSpeed("0.04"))
        assertEquals(0.1f, parsePersistedVideoPlaybackSpeed("0.1"))
        assertEquals(2f, parsePersistedVideoPlaybackSpeed("2.0"))
    }

    @Test
    fun missingInvalidOrRemovedValueFallsBackToDefault() {
        assertEquals(DEFAULT_VIDEO_PLAYBACK_SPEED, parsePersistedVideoPlaybackSpeed(null))
        assertEquals(DEFAULT_VIDEO_PLAYBACK_SPEED, parsePersistedVideoPlaybackSpeed("invalid"))
        assertEquals(DEFAULT_VIDEO_PLAYBACK_SPEED, parsePersistedVideoPlaybackSpeed("0.75"))
        assertEquals(DEFAULT_VIDEO_PLAYBACK_SPEED, parsePersistedVideoPlaybackSpeed("1.5"))
    }
}
