/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

internal const val DEFAULT_VIDEO_PLAYBACK_SPEED = 1f

internal val SUPPORTED_VIDEO_PLAYBACK_SPEEDS =
    listOf(0.04f, 0.1f, 0.25f, 0.5f, DEFAULT_VIDEO_PLAYBACK_SPEED, 2f)

internal fun parsePersistedVideoPlaybackSpeed(value: String?): Float {
    val speed = value?.toFloatOrNull() ?: return DEFAULT_VIDEO_PLAYBACK_SPEED
    return speed.takeIf { it in SUPPORTED_VIDEO_PLAYBACK_SPEEDS }
        ?: DEFAULT_VIDEO_PLAYBACK_SPEED
}
