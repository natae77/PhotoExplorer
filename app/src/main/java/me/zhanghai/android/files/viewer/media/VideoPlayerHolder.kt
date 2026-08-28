/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java8.nio.file.Path
import me.zhanghai.android.files.file.fileProviderUri

/**
 * Owns the single ExoPlayer instance of the viewer, see spec 11 section 5.2.
 *
 * ViewPager2 keeps up to three pages alive (offscreenPageLimit = 1) and hardware decoders are a
 * handful per device, so one instance is attached to whichever page is current instead of one
 * instance per page.
 */
@OptIn(UnstableApi::class)
class VideoPlayerHolder(context: Context, listener: Player.Listener) {
    private val player: ExoPlayer =
        ExoPlayer.Builder(context.applicationContext)
            // Pauses when a phone call or another app takes the focus, see spec 11 section 5.5.
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            // Pauses when headphones are unplugged.
            .setHandleAudioBecomingNoisy(true)
            // Media3 defaults to 5s back and 15s forward. Spec 11 section 6.1 says 10s both ways.
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(listener)
            }

    /** The path currently loaded, or null when nothing is. */
    var currentPath: Path? = null
        private set

    private var attachedView: PlayerView? = null

    val exoPlayer: ExoPlayer
        get() = player

    /**
     * Attaches the player to [view] and starts [path] at [positionMillis].
     *
     * Passing the path that is already attached does nothing at all, not even resume: see plan 12
     * 3.2.1. This is safe to call from page callbacks that fire more than once.
     */
    fun play(path: Path, view: PlayerView, positionMillis: Long) {
        if (currentPath == path && attachedView === view) {
            return
        }
        detach()
        attachedView = view
        view.player = player
        currentPath = path
        player.setMediaItem(MediaItem.fromUri(path.fileProviderUri), positionMillis)
        player.prepare()
        player.play()
    }

    /** Detaches from the current page without releasing the player. */
    fun detach() {
        player.stop()
        attachedView?.player = null
        attachedView = null
        currentPath = null
    }

    /** Position of what is playing now, or [C.TIME_UNSET] when there is nothing. */
    val currentPositionMillis: Long
        get() = if (currentPath != null) player.currentPosition else C.TIME_UNSET

    fun pause() {
        player.pause()
    }

    fun release() {
        attachedView?.player = null
        attachedView = null
        currentPath = null
        player.release()
    }
}
