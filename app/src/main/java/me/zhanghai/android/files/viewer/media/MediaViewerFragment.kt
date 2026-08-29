/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.TimeBar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import dev.chrisbanes.insetter.applySystemWindowInsetsToPadding
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.MediaViewerFragmentBinding
import me.zhanghai.android.files.databinding.MediaViewerVideoItemBinding
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.file.guessFromPath
import me.zhanghai.android.files.provider.common.delete
import me.zhanghai.android.files.ui.DepthPageTransformer
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableListParceler
import me.zhanghai.android.files.util.ParcelableState
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.createSendStreamIntent
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.extraPathList
import me.zhanghai.android.files.util.fadeInUnsafe
import me.zhanghai.android.files.util.fadeOutUnsafe
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.getState
import me.zhanghai.android.files.util.mediumAnimTime
import me.zhanghai.android.files.util.putState
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.viewModels
import me.zhanghai.android.files.util.withChooser
import me.zhanghai.android.systemuihelper.SystemUiHelper
import java.io.IOException

@OptIn(UnstableApi::class)
class MediaViewerFragment :
    Fragment(), ConfirmDeleteDialogFragment.Listener, VideoDetailsDialogFragment.Listener {
    private val args by args<Args>()

    private val viewModel by viewModels { { MediaViewerViewModel() } }
    private val argsPaths by lazy { args.intent.extraPathList }

    private lateinit var paths: MutableList<Path>

    private lateinit var binding: MediaViewerFragmentBinding

    private lateinit var systemUiHelper: SystemUiHelper

    private lateinit var adapter: MediaViewerAdapter

    private var playerHolder: VideoPlayerHolder? = null

    private var isSystemUiVisible = true

    /** Wrapping twice would stack disc on disc, see spec 11a section 3.3. */
    private var isOverflowIconScrimmed = false

    /** A finger on the slider keeps the player buffering, see spec 11a section 6.1. */
    private var isScrubbing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        paths = (savedInstanceState?.getState<State>()?.paths ?: argsPaths).toMutableList()

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        MediaViewerFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (paths.isEmpty()) {
            // TODO: Show a toast.
            finish()
            return
        }

        val activity = activity as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.apply {
            setDisplayHomeAsUpEnabled(true)
            // The app bar has no background any more and shows nothing but its two icons.
            // See spec 11a section 3.2.
            setDisplayShowTitleEnabled(false)
        }
        // The app bar is transparent, so the media reaches the status bar. See spec 11a section 3.1.
        activity.window.statusBarColor = Color.TRANSPARENT
        // The icons would disappear over a bright photo without a scrim, see spec 11a section 3.3.
        binding.toolbar.navigationIcon =
            binding.toolbar.navigationIcon?.withCircleScrim(requireContext())
        binding.appBarLayout.applySystemWindowInsetsToPadding(left = true, top = true, right = true)
        binding.playerControlView.applySystemWindowInsetsToPadding(
            left = true, bottom = true, right = true
        )
        // Dragging the slider makes the player buffer for as long as the finger is down, which
        // would put a spinner in the middle of the picture. See spec 11a section 6.1.
        binding.playerControlView
            .findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)
            ?.addListener(object : TimeBar.OnScrubListener {
                override fun onScrubStart(timeBar: TimeBar, position: Long) {
                    isScrubbing = true
                    currentVideoHolder?.progress?.end(DelayedProgress.Reason.BUFFERING)
                }

                override fun onScrubMove(timeBar: TimeBar, position: Long) {}

                override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                    isScrubbing = false
                }
            })
        systemUiHelper = SystemUiHelper(
            activity, SystemUiHelper.LEVEL_IMMERSIVE, SystemUiHelper.FLAG_IMMERSIVE_STICKY
        ) { visible: Boolean ->
            isSystemUiVisible = visible
            binding.appBarLayout.animate()
                .alpha(if (visible) 1f else 0f)
                .translationY(if (visible) 0f else -binding.appBarLayout.bottom.toFloat())
                .setDuration(mediumAnimTime.toLong())
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
            // The controls ride with the app bar, see spec 11 section 6.2.
            updatePlayerControlVisibility()
        }
        // This will set up window flags.
        systemUiHelper.show()
        adapter = MediaViewerAdapter(
            viewLifecycleOwner,
            { systemUiHelper.toggle() },
            // Swiping down should be the same as pressing back.
            { activity.onBackPressedDispatcher.onBackPressed() }
        ).apply { replace(paths) }
        binding.viewPager.apply {
            // 1 is the default for the old androidx.viewpager.widget.ViewPager.
            offscreenPageLimit = 1
            adapter = this@MediaViewerFragment.adapter
            // ViewPager saves its position and will restore it later.
            setCurrentItem(args.position, false)
            setPageTransformer(DepthPageTransformer)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    // Do not start here. Fast flinging fires this for every page passed, and each
                    // one would briefly play sound. See spec 11 section 5.1.
                    stopPlaybackIfPageChanged()
                    updatePlayerControlVisibility()
                    // The playback speed and details items only exist on video pages.
                    requireActivity().invalidateOptionsMenu()
                }

                override fun onPageScrollStateChanged(state: Int) {
                    if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        startPlaybackIfVideoPage()
                    }
                }
            })
            // The initial page never scrolls, so SCROLL_STATE_IDLE never arrives for it.
            // See plan 12 3.2.2.
            doOnPreDraw { startPlaybackIfVideoPage() }
        }
    }

    override fun onResume() {
        super.onResume()

        // SCROLL_STATE_IDLE does not come again, and the page view may not be attached yet.
        // See plan 12 5.3.
        binding.viewPager.doOnPreDraw { startPlaybackIfVideoPage() }
    }

    override fun onPause() {
        super.onPause()

        // Holding on to a decoder in the background gets in the way of other apps, see spec 11
        // section 5.3.
        val holder = playerHolder ?: return
        holder.currentPath?.let { rememberPosition(holder, it) }
        holder.release()
        playerHolder = null
        binding.playerControlView.player = null
    }

    override fun onDestroyView() {
        super.onDestroyView()

        playerHolder?.release()
        playerHolder = null
        // The new view will get a fresh, unwrapped overflow icon.
        isOverflowIconScrimmed = false
    }

    private fun stopPlaybackIfPageChanged() {
        val holder = playerHolder ?: return
        val playingPath = holder.currentPath ?: return
        if (playingPath != currentPath) {
            rememberPosition(holder, playingPath)
            restoreVideoPage(playingPath)
            holder.detach()
        }
    }

    private fun rememberPosition(holder: VideoPlayerHolder, path: Path) {
        val position = holder.currentPositionMillis
        if (position != C.TIME_UNSET && position > 0) {
            viewModel.playbackPositions[path] = position
        }
    }

    /**
     * Puts the page of [path] back to its thumbnail, see plan 12 3.3.
     *
     * Leaving this to MediaViewerAdapter.bindVideo() is not enough: with offscreenPageLimit = 1 the
     * page next to the current one is never rebound, so it would stay black until it plays again.
     */
    private fun restoreVideoPage(path: Path) {
        val position = paths.indexOf(path)
        if (position == -1) {
            return
        }
        val holder = videoHolderAt(position) ?: return
        // The page we are leaving must not show buffering of the video we took away from it.
        holder.progress.end(DelayedProgress.Reason.BUFFERING)
        val videoBinding = holder.binding
        videoBinding.playerView.isVisible = false
        videoBinding.thumbnailImage.animate().cancel()
        videoBinding.thumbnailImage.alpha = 1f
        videoBinding.thumbnailImage.isVisible = true
    }

    private fun startPlaybackIfVideoPage() {
        val path = currentPath
        if (!path.isPlayableVideo) {
            playerHolder?.detach()
            return
        }
        val playerView = currentVideoBinding?.playerView ?: return
        val holder = playerHolder ?: VideoPlayerHolder(requireContext(), playerListener)
            .also {
                playerHolder = it
                binding.playerControlView.player = it.exoPlayer
                // Otherwise the picture stands still until the finger is lifted.
                binding.playerControlView.setTimeBarScrubbingEnabled(true)
            }
        // Already on this page: leave it alone, see plan 12 3.2.1.
        if (holder.currentPath == path) {
            return
        }
        playerView.isVisible = true
        holder.play(path, playerView, viewModel.playbackPositions[path] ?: 0L)
        // The speed is shared by every video of the session, see spec 11 section 6.3.
        holder.exoPlayer.setPlaybackSpeed(viewModel.playbackSpeed)
    }

    /** Photo pages never show the controls, see spec 11 section 6.2. */
    private fun updatePlayerControlVisibility() {
        // PlayerControlView.isVisible() is its own read-only method, so the View extension
        // property of the same name is not usable here.
        val controlView = binding.playerControlView
        if (isSystemUiVisible && currentPath.isPlayableVideo) {
            controlView.visibility = View.VISIBLE
            controlView.show()
        } else {
            controlView.hide()
            controlView.visibility = View.GONE
        }
    }

    /** The binding of the current page, or null when it is not a bound video page. */
    private val currentVideoHolder: MediaViewerAdapter.VideoViewHolder?
        get() = videoHolderAt(binding.viewPager.currentItem)

    private val currentVideoBinding: MediaViewerVideoItemBinding?
        get() = currentVideoHolder?.binding

    /**
     * The binding of the video page at [position], or null when there is none right now.
     *
     * ViewPager2 hides its RecyclerView and offers no public way to reach a page, and a page that
     * is off screen may have no view at all. Callers give up quietly when this returns null.
     */
    private fun videoHolderAt(position: Int): MediaViewerAdapter.VideoViewHolder? {
        val recyclerView = binding.viewPager.getChildAt(0) as? RecyclerView ?: return null
        val holder = recyclerView.findViewHolderForAdapterPosition(position)
        return holder as? MediaViewerAdapter.VideoViewHolder
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            error.printStackTrace()
            val path = playerHolder?.currentPath ?: return
            // The page that failed may not be the page on screen any more, see plan 12 8.1.1.
            if (path != currentPath) {
                return
            }
            showPlaybackError(path, error)
        }

        override fun onRenderedFirstFrame() {
            currentVideoBinding?.thumbnailImage?.fadeOutUnsafe()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateBufferingProgress(playbackState)
            if (playbackState == Player.STATE_READY) {
                // videoFormat and duration are known only now, see spec 11 section 7.1.
                updateVideoDetailsSheet()
            }
            if (playbackState == Player.STATE_ENDED) {
                // Otherwise coming back to this video would start it at its last frame.
                playerHolder?.currentPath?.let { viewModel.playbackPositions.remove(it) }
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            // Our own menu is the only way to change this now, but keeping our copy in sync
            // keeps the checked menu item honest and stops the next video from reverting the
            // speed.
            if (playbackParameters.speed != viewModel.playbackSpeed) {
                viewModel.playbackSpeed = playbackParameters.speed
                requireActivity().invalidateOptionsMenu()
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            updateVideoDetailsSheet()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // A view flag instead of a window flag: it is cleared when the view leaves the window.
            binding.root.keepScreenOn = isPlaying
        }
    }

    /** Only the page that owns the player may show its buffering, see spec 11a section 6.1. */
    private fun updateBufferingProgress(playbackState: Int) {
        if (playerHolder?.currentPath != currentPath) {
            return
        }
        val holder = currentVideoHolder ?: return
        if (playbackState == Player.STATE_BUFFERING && !isScrubbing) {
            holder.progress.begin(DelayedProgress.Reason.BUFFERING)
        } else {
            holder.progress.end(DelayedProgress.Reason.BUFFERING)
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        if (paths.isEmpty()) {
            // We did finish the activity in onActivityCreated(), however we will still be called
            // here before the activity is actually finished.
            return
        }

        // onPageSelected() never fires for the initial page because the callback is registered
        // after setCurrentItem().
        updatePlayerControlVisibility()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putState(State(paths))
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.media_viewer, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        // The overflow icon only exists once the menu has been created, so it cannot be wrapped
        // along with the navigation icon in onActivityCreated().
        if (!isOverflowIconScrimmed) {
            val overflowIcon = binding.toolbar.overflowIcon
            if (overflowIcon != null) {
                binding.toolbar.overflowIcon = overflowIcon.withCircleScrim(requireContext())
                isOverflowIconScrimmed = true
            }
        }
        val isVideo = currentPath.isPlayableVideo
        menu.findItem(R.id.action_playback_speed).isVisible = isVideo
        menu.findItem(R.id.action_video_details).isVisible = isVideo
        if (isVideo) {
            // indexOf() is not available for FloatArray because of NaN.
            val index = PLAYBACK_SPEEDS.indexOfFirst { it == viewModel.playbackSpeed }
            // Only the matching item is touched: in a checkableBehavior="single" group,
            // setChecked(false) also makes that item the checked one (MenuItemImpl.setChecked()).
            if (index != -1) {
                menu.findItem(SPEED_ITEM_IDS[index]).isChecked = true
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_speed_0_25 -> { setPlaybackSpeed(0.25f); true }
            R.id.action_speed_0_5 -> { setPlaybackSpeed(0.5f); true }
            R.id.action_speed_0_75 -> { setPlaybackSpeed(0.75f); true }
            R.id.action_speed_1 -> { setPlaybackSpeed(1f); true }
            R.id.action_speed_1_5 -> { setPlaybackSpeed(1.5f); true }
            R.id.action_speed_2 -> { setPlaybackSpeed(2f); true }
            R.id.action_video_details -> {
                showVideoDetails()
                true
            }
            R.id.action_delete -> {
                confirmDelete()
                true
            }
            R.id.action_share -> {
                share()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun confirmDelete() {
        ConfirmDeleteDialogFragment.show(currentPath, this)
    }

    override fun delete(path: Path) {
        // Let go of the file before unlinking it, see spec 11 section 9.
        playerHolder?.let { if (it.currentPath == path) it.detach() }
        viewModel.playbackPositions.remove(path)
        viewModel.videoFileDetails.remove(path)
        try {
            path.delete()
        } catch (e: IOException) {
            e.printStackTrace()
            showToast(e.toString())
            return
        }
        paths.removeAll(listOf(path))
        if (paths.isEmpty()) {
            finish()
            return
        }
        adapter.replace(paths)
        // ViewPager only asynchronously sets current item to 0, which isn't a desirable behavior
        // for us and would leave currentItem out of bounds for currentPath.
        if (binding.viewPager.currentItem > paths.lastIndex) {
            binding.viewPager.currentItem = paths.lastIndex
        }
        // Work around blank screen due to ViewPager2.PageTransformer not being called (and thus the
        // next item keeps its 0 alpha) when we have offscreenPageLimit = 1.
        binding.viewPager.doOnPreDraw {
            binding.viewPager.requestTransform()
            // The page that took the deleted one's place may be a video.
            startPlaybackIfVideoPage()
        }
    }

    private fun showPlaybackError(path: Path, error: PlaybackException) {
        val holder = currentVideoHolder ?: return
        // A pending show would otherwise put a spinner on top of the error message.
        holder.progress.endAll()
        val videoBinding = holder.binding
        videoBinding.playerView.isVisible = false
        videoBinding.thumbnailImage.isVisible = false
        videoBinding.errorText.text = getString(
            R.string.media_viewer_playback_error_format, error.errorCodeName
        )
        // Another app cannot open a file that is missing or unreadable either, so the button is
        // only for codec failures. Spec 11 section 8.
        val isFileError = error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
            || error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION
        videoBinding.openWithButton.isVisible = !isFileError
        videoBinding.openWithButton.setOnClickListener(
            if (isFileError) null else View.OnClickListener { openWithAnotherApp(path) }
        )
        videoBinding.errorLayout.fadeInUnsafe(true)
    }

    private fun openWithAnotherApp(path: Path) {
        val mimeType = MimeType.guessFromPath(path.toString())
        val intent = path.fileProviderUri.createViewIntent(mimeType)
            .apply { extraPath = path }
            .withChooser()
        startActivitySafe(intent)
    }

    override fun getVideoDetails(path: Path): VideoDetails {
        // The player only knows about the page it is attached to.
        val player = playerHolder?.takeIf { it.currentPath == path }?.exoPlayer
        return buildVideoDetails(
            path, viewModel.videoFileDetails[path], player?.videoFormat, player?.duration
        )
    }

    private fun showVideoDetails() {
        val path = currentPath
        VideoDetailsDialogFragment.show(path, this)
        loadVideoFileDetails(path)
    }

    private fun loadVideoFileDetails(path: Path) {
        if (path in viewModel.videoFileDetails) {
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val fileDetails = try {
                withContext(Dispatchers.IO) { readVideoFileDetails(path) }
            } catch (e: Exception) {
                e.printStackTrace()
                return@launch
            }
            viewModel.videoFileDetails[path] = fileDetails
            updateVideoDetailsSheet()
        }
    }

    private fun updateVideoDetailsSheet() {
        (childFragmentManager.findFragmentByTag(VideoDetailsDialogFragment.TAG)
            as? VideoDetailsDialogFragment)?.updateDetails()
    }

    private fun setPlaybackSpeed(speed: Float) {
        viewModel.playbackSpeed = speed
        playerHolder?.exoPlayer?.setPlaybackSpeed(speed)
    }

    private fun share() {
        val path = currentPath
        // Videos must not go out as image/*, see spec 11 section 9.
        val mimeType = MimeType.guessFromPath(path.toString())
        val intent = path.fileProviderUri.createSendStreamIntent(mimeType)
            .apply { extraPath = path }
            .withChooser()
        startActivitySafe(intent)
    }

    private val currentPath: Path
        get() = paths[binding.viewPager.currentItem]

    companion object {
        // Spec 11 section 6.3. 0.25 is there to slow fast motion down, e.g. a golf swing.
        private val PLAYBACK_SPEEDS = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f)

        private val SPEED_ITEM_IDS = intArrayOf(
            R.id.action_speed_0_25, R.id.action_speed_0_5, R.id.action_speed_0_75,
            R.id.action_speed_1, R.id.action_speed_1_5, R.id.action_speed_2
        )
    }

    @Parcelize
    class Args(val intent: Intent, val position: Int) : ParcelableArgs

    @Parcelize
    private class State(val paths: @WriteWith<ParcelableListParceler> List<Path>) : ParcelableState
}
