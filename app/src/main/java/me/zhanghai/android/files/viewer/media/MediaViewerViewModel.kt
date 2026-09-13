/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java8.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.provider.common.readAttributes
import me.zhanghai.android.files.provider.linux.isLinuxPath
import java8.nio.file.attribute.BasicFileAttributes

/**
 * State that outlives a configuration change but dies with the viewer, see spec 11 sections 5.4
 * and 6.3. Nothing here is persisted, see decision D8.
 */
class MediaViewerViewModel : ViewModel() {
    /** Playback position per video, in milliseconds. */
    val playbackPositions = mutableMapOf<Path, Long>()

    /** Shared by every video in this session, back to 1x when the viewer is closed. */
    var playbackSpeed = 1f

    /** Shared by every video in this viewer session; frame stepping is the deliberate default. */
    var videoSeekUnit = VideoSeekUnit.FRAME

    var frameCursor: VideoFrameCursor? = null

    private val mutableFrameTimelineState = MutableLiveData<VideoFrameTimelineState>(
        VideoFrameTimelineState.NotRequested
    )
    val frameTimelineState: LiveData<VideoFrameTimelineState> = mutableFrameTimelineState

    private val frameTimelineCache = LinkedHashMap<FrameTimelineCacheKey, VideoFrameTimeline>(
        4, 0.75f, true
    )
    private var cachedTimelineBytes = 0L
    private var frameTimelineJob: Job? = null
    private var frameTimelineGeneration = 0L

    /** The file half of the details sheet, see plan 12 7.3. */
    val videoFileDetails = mutableMapOf<Path, VideoFileDetails>()

    fun requestFrameTimeline(
        context: Context,
        path: Path,
        selectedTrack: VideoTrackIdentity?
    ) {
        val state = mutableFrameTimelineState.value
        if ((state is VideoFrameTimelineState.Loading || state is VideoFrameTimelineState.Ready)
            && state.path == path) {
            return
        }
        frameTimelineJob?.cancel()
        val generation = ++frameTimelineGeneration
        mutableFrameTimelineState.value = VideoFrameTimelineState.Loading(path, generation)
        val applicationContext = context.applicationContext
        frameTimelineJob = viewModelScope.launch {
            try {
                val key = withContext(Dispatchers.IO) {
                    if (path.isLinuxPath) {
                        val file = path.toFile()
                        FrameTimelineCacheKey(path, file.length(), file.lastModified())
                    } else {
                        val attributes = path.readAttributes(BasicFileAttributes::class.java)
                        FrameTimelineCacheKey(
                            path, attributes.size(), attributes.lastModifiedTime().toMillis()
                        )
                    }
                }
                val timeline = frameTimelineCache[key] ?: withContext(Dispatchers.IO) {
                    VideoFrameTimelineReader.read(applicationContext, path, selectedTrack)
                }
                if (generation != frameTimelineGeneration) return@launch
                if (!frameTimelineCache.containsKey(key)) {
                    frameTimelineCache[key] = timeline
                    cachedTimelineBytes += timeline.byteSize
                    trimFrameTimelineCache(key)
                }
                mutableFrameTimelineState.value = VideoFrameTimelineState.Ready(
                    path, generation, timeline
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                if (generation == frameTimelineGeneration) {
                    mutableFrameTimelineState.value = VideoFrameTimelineState.Unavailable(
                        path, generation, e.message ?: e.javaClass.simpleName
                    )
                }
            }
        }
    }

    fun cancelFrameTimelineRequest() {
        frameTimelineJob?.cancel()
        frameTimelineJob = null
        ++frameTimelineGeneration
        frameCursor = null
        mutableFrameTimelineState.value = VideoFrameTimelineState.NotRequested
    }

    fun removeFrameTimeline(path: Path) {
        val iterator = frameTimelineCache.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.path == path) {
                cachedTimelineBytes -= entry.value.byteSize
                iterator.remove()
            }
        }
        if (mutableFrameTimelineState.value?.path == path) cancelFrameTimelineRequest()
    }

    private fun trimFrameTimelineCache(pinnedKey: FrameTimelineCacheKey) {
        val iterator = frameTimelineCache.iterator()
        while (cachedTimelineBytes > MAX_CACHED_TIMELINE_BYTES && iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key == pinnedKey) continue
            cachedTimelineBytes -= entry.value.byteSize
            iterator.remove()
        }
    }

    companion object {
        private const val MAX_CACHED_TIMELINE_BYTES = 32L * 1024L * 1024L
    }
}

sealed class VideoFrameTimelineState(open val path: Path?) {
    data object NotRequested : VideoFrameTimelineState(null)
    data class Loading(override val path: Path, val generation: Long) :
        VideoFrameTimelineState(path)
    data class Ready(
        override val path: Path,
        val generation: Long,
        val timeline: VideoFrameTimeline
    ) : VideoFrameTimelineState(path)
    data class Unavailable(
        override val path: Path,
        val generation: Long,
        val reason: String
    ) : VideoFrameTimelineState(path)
}

private data class FrameTimelineCacheKey(
    val path: Path,
    val size: Long,
    val lastModifiedMillis: Long
)
