/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import androidx.lifecycle.ViewModel
import java8.nio.file.Path

/**
 * State that outlives a configuration change but dies with the viewer, see spec 11 sections 5.4
 * and 6.3. Nothing here is persisted, see decision D8.
 */
class MediaViewerViewModel : ViewModel() {
    /** Playback position per video, in milliseconds. */
    val playbackPositions = mutableMapOf<Path, Long>()

    /** Shared by every video in this session, back to 1x when the viewer is closed. */
    var playbackSpeed = 1f
}
