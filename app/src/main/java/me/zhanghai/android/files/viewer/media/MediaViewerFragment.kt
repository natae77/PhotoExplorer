/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnPreDraw
import androidx.core.view.drawToBitmap
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
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
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
import me.zhanghai.android.files.util.addOnBackPressedCallback
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
import kotlin.math.roundToInt

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
    private var renderedVideoPath: Path? = null
    private var returnState = ReturnState.IDLE
    private var pagerScrollState = ViewPager2.SCROLL_STATE_IDLE
    private var viewportStatus: MediaViewerViewportCoordinator.Status? = null

    private var isSystemUiVisible = true

    /** Wrapping twice would stack disc on disc, see spec 11a section 3.3. */
    private var isOverflowIconScrimmed = false

    /** A finger on the slider keeps the player buffering, see spec 11a section 6.1. */
    private var isScrubbing = false

    /**
     * Whether we are on the way out, see plan 18 section 3.2.1.
     *
     * The activity's shared element callback is called in both directions, and the guard that
     * refuses to fly an empty rectangle back has to apply to the return only: on the way in, the
     * transition image is legitimately empty when onMapSharedElements() runs, because nothing has
     * filled it in yet.
     */
    val isReturning: Boolean
        get() = returnState == ReturnState.RETURNING

    /**
     * Whether we were opened with a shared element at all, see plan 18 section 3.7 (F1, F2).
     *
     * A video opened from list mode, or a photo handed to us by another app, arrives with no
     * ActivityOptions and leaves on the plain window animation. Emptying the pager for those would
     * put a blank viewer on screen for the whole of it.
     */
    private var hasSharedElement = false

    /**
     * Whether the way in is still running, see plan 18 section 3.3.
     *
     * ⚠️ ViewPager2 dispatches onPageSelected() for the page we opened on during its first layout,
     * which lands in the middle of the enter transition. Without this the safety net there would
     * reveal the pager and start fading the picture out before the tile has finished growing - the
     * exact double image this is all here to avoid.
     */
    private var isEntering = false

    /** The transition image, or null while there is no view, see plan 18 section 3.2.2. */
    val transitionImageOrNull: ImageView?
        get() =
            if (view != null && this::binding.isInitialized) binding.transitionImage else null

    private val swipeDownListener = object : SwipeDownDismissLayout.Listener {
        override fun canStartDrag(layout: SwipeDownDismissLayout): Boolean {
            if (layout !== currentPageRoot() || returnState != ReturnState.IDLE || isEntering) {
                return false
            }
            val sessionId = (activity as? MediaViewerActivity)?.viewportSessionId ?: return true
            if (pagerScrollState != ViewPager2.SCROLL_STATE_IDLE) return false
            val status = viewportStatus
            if (status == null || status.request.sessionId != sessionId
                || status.request.path != currentPath) {
                return false
            }
            return status.preparation != MediaViewerViewportCoordinator.Preparation.PENDING
        }

        override fun onDragStarted(layout: SwipeDownDismissLayout) {
            if (!canRevealFileListBehind(layout)) return
            (activity as? MediaViewerActivity)
                ?.setViewerBackgroundAlpha(BACKGROUND_ALPHA_AT_DRAG_START)
        }

        override fun onDragProgress(layout: SwipeDownDismissLayout, progress: Float) {
            if (!canRevealFileListBehind(layout)) return
            val revealProgress = (progress / BACKGROUND_FULL_REVEAL_FRACTION).coerceIn(0f, 1f)
            val alpha = BACKGROUND_ALPHA_AT_DRAG_START * (1f - revealProgress)
            (activity as? MediaViewerActivity)?.setViewerBackgroundAlpha(alpha)
        }

        override fun onDragCancelled(layout: SwipeDownDismissLayout) {
            if (layout !== currentPageRoot()) return
            (activity as? MediaViewerActivity)?.restoreViewerBackground()
        }

        override fun onDismissed(layout: SwipeDownDismissLayout) {
            if (layout !== currentPageRoot()) return
            (activity as? MediaViewerActivity)?.cancelViewerBackgroundAnimation()
            finishWithReturnTransition()
        }
    }

    private fun canRevealFileListBehind(layout: SwipeDownDismissLayout): Boolean =
        layout === currentPageRoot()
            && returnState == ReturnState.IDLE
            && !isEntering
            && (activity as? MediaViewerActivity)?.canRevealFileList == true
            && pagerScrollState == ViewPager2.SCROLL_STATE_IDLE
            && viewportStatus?.let {
                it.request.path == currentPath
                    && it.preparation == MediaViewerViewportCoordinator.Preparation.READY
            } == true

    private val viewportListener = MediaViewerViewportCoordinator.ViewerListener { status ->
        val sessionId = (activity as? MediaViewerActivity)?.viewportSessionId
        if (status.request.sessionId == sessionId) {
            viewportStatus = status
            if (status.preparation != MediaViewerViewportCoordinator.Preparation.READY) {
                (activity as? MediaViewerActivity)?.setViewerBackgroundAlpha(1f)
            }
        }
    }

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
            swipeDownListener
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
                    (activity as? MediaViewerActivity)?.setViewerBackgroundAlpha(1f)
                    val selectedPath = paths.getOrNull(position)
                    // IDLE can synchronously publish READY before ViewPager2 delivers page
                    // selection. Keep that status when it already belongs to this selected page;
                    // clearing it here would disable swipe-down until another horizontal gesture
                    // produces a second IDLE callback.
                    if (viewportStatus?.request?.path != selectedPath) {
                        viewportStatus = null
                    }
                    // Do not start here. Fast flinging fires this for every page passed, and each
                    // one would briefly play sound. See spec 11 section 5.1.
                    stopPlaybackIfPageChanged()
                    // ⚠️ Gated: ViewPager2 also fires this for the page we opened on, in the
                    // middle of the enter transition. A real page change only happens once the way
                    // in is over, and then anything left of the entering picture is stale.
                    if (!isEntering) {
                        hideTransitionImageWhenPageReady()
                    }
                    updatePlayerControlVisibility()
                    // The playback speed and details items only exist on video pages.
                    requireActivity().invalidateOptionsMenu()
                }

                override fun onPageScrollStateChanged(state: Int) {
                    pagerScrollState = state
                    if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        publishCurrentViewportIfIdle()
                        startPlaybackIfVideoPage()
                    }
                }
            })
            // The initial page never scrolls, so SCROLL_STATE_IDLE never arrives for it.
            // See plan 12 3.2.2.
            doOnPreDraw {
                publishCurrentViewportIfIdle()
                startPlaybackIfVideoPage()
            }
        }
        (activity as? MediaViewerActivity)?.viewportSessionId?.let {
            MediaViewerViewportCoordinator.registerViewer(it, viewportListener)
        }
        // The one place every way out passes through, see plan 18 section 3.6. Dragging down and
        // the system back button already come here; the toolbar arrow is sent here by
        // MediaViewerActivity.onSupportNavigateUp().
        addOnBackPressedCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithReturnTransition()
            }
        })
    }

    private fun finishWithReturnTransition() {
        if (returnState != ReturnState.IDLE) return
        val exitActivity = requireActivity()
        val exitView = view
        val exitPath = currentPath
        val surface = currentVideoBinding?.playerView?.videoSurfaceView as? SurfaceView
        lockReturnInput()
        if (!hasSharedElement) {
            prepareReturnTransition()
            // The translucent viewer window does not receive Android's ordinary close animation,
            // so explicitly restore it for list/grid launches without a shared element.
            (exitActivity as MediaViewerActivity).finishWithPlainAnimation()
            return
        }
        if (surface == null || renderedVideoPath != exitPath
            || Build.VERSION.SDK_INT < Build.VERSION_CODES.N
            || !surface.holder.surface.isValid || surface.width <= 0 || surface.height <= 0) {
            prepareReturnTransition()
            exitActivity.finishAfterTransition()
            return
        }
        // HDR playback uses SurfaceView. Copy its last frame before hiding/releasing the surface.
        returnState = ReturnState.PREPARING_RETURN
        playerHolder?.exoPlayer?.pause()
        val handler = Handler(Looper.getMainLooper())
        var completed = false
        fun complete(drawable: Drawable?) {
            if (completed) return
            completed = true
            if (view !== exitView || exitActivity.isFinishing || exitActivity.isDestroyed) return
            prepareReturnTransition(drawable?.takeIf { currentPath == exitPath })
            exitActivity.finishAfterTransition()
        }
        val timeout = Runnable { complete(null) }
        handler.postDelayed(timeout, 500)
        try {
            val frame = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(surface, frame, { result ->
                handler.removeCallbacks(timeout)
                logMediaTransition("viewer surface copy: result=$result")
                if (!completed && result == PixelCopy.SUCCESS) {
                    complete(BitmapDrawable(resources, frame))
                } else {
                    frame.recycle()
                    complete(null)
                }
            }, handler)
        } catch (exception: Exception) {
            handler.removeCallbacks(timeout)
            logMediaTransition("viewer surface copy failed: $exception")
            complete(null)
        }
    }

    private fun publishCurrentViewportIfIdle() {
        if (pagerScrollState != ViewPager2.SCROLL_STATE_IDLE || returnState != ReturnState.IDLE
            || paths.isEmpty()) {
            return
        }
        val path = currentPath
        requireActivity().setResult(Activity.RESULT_OK, Intent().apply { extraPath = path })
        val sessionId = (activity as? MediaViewerActivity)?.viewportSessionId ?: return
        MediaViewerViewportCoordinator.request(sessionId, path)
    }

    /**
     * Hands the grid what it needs to fly the current media back into its tile, see plan 18
     * sections 3.4 and 3.6.
     *
     * All of it happens in one frame, right before finishAfterTransition() captures the shared
     * element.
     */
    private fun prepareReturnTransition(videoFrame: Drawable? = null) {
        returnState = ReturnState.RETURNING
        val activity = requireActivity()
        if (!hasSharedElement) {
            // Nothing to return to. Leaving the pager alone keeps the ordinary window animation
            // showing the picture rather than an empty screen. See plan 18 section 3.7.
            logMediaTransition("viewer exit: no shared element, plain finish")
            activity.setResult(Activity.RESULT_CANCELED)
            return
        }
        val transitionImage = binding.transitionImage
        val drawable = videoFrame ?: returnDrawable()
        if (drawable == null) {
            // Nothing worth sending: a zoomed photo, a page still loading, or one that failed.
            // RESULT_CANCELED stops the grid from remapping, and the empty drawable is what stops
            // our own callback from flying a blank rectangle. See plan 18 section 3.7.
            logMediaTransition("viewer exit: setResult(CANCELED), nothing to send")
            transitionImage.animate().cancel()
            transitionImage.setImageDrawable(null)
            activity.setResult(Activity.RESULT_CANCELED)
            return
        }
        logMediaTransition("viewer exit: setResult(OK) for $currentPath")
        activity.setResult(Activity.RESULT_OK, Intent().apply { extraPath = currentPath })
        transitionImage.apply {
            animate().cancel()
            alpha = 1f
            setImageDrawable(drawable)
            // Carry on from wherever the downward drag left the page, see plan 18 section 3.6 (2).
            // Alpha is left alone: a shared element should stay opaque while it travels.
            val page = currentPageRoot()
            translationY = page?.translationY ?: 0f
            scaleX = page?.scaleX ?: 1f
            scaleY = page?.scaleY ?: 1f
        }
        // The window lets the return transition overlap, so the same picture would otherwise be on
        // screen twice - one flying to the tile, one fading out in place. Plan 18 3.6 (1).
        binding.viewPager.isVisible = false
        binding.appBarLayout.isVisible = false
        binding.playerControlView.visibility = View.GONE
    }

    private fun lockReturnInput() {
        binding.viewPager.isUserInputEnabled = false
        currentPageRoot()?.isEnabled = false
        binding.playerControlView.isEnabled = false
    }

    /** The picture to fly back with, or null when this page cannot take part (F4, F5). */
    private fun returnDrawable(): Drawable? {
        // A zoomed photo is not where it started, so there is nothing sensible to fly. The drag to
        // dismiss test already asks exactly this question. See plan 18 section 3.7.
        if (currentPageRoot()?.canDismiss?.invoke() == false) {
            return null
        }
        val drawable = (currentPageContent() as? PageContent.Ready)?.drawable ?: return null
        return drawable.toSoftwareDrawable()
    }

    /**
     * ⚠️ A hardware bitmap cannot be handed to the transition.
     *
     * The framework captures the shared element by drawing it into a software Canvas, which throws
     * on a hardware bitmap - and Coil hands those out for the photo pages and the video thumbnail.
     * The copy is only paid for when the bitmap really is one; the tiled photo and the video frame
     * are drawn by us and are software already. Returning null falls back rather than crashing.
     */
    private fun Drawable.toSoftwareDrawable(): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return this
        }
        val bitmap = (this as? BitmapDrawable)?.bitmap ?: return this
        if (bitmap.config != Bitmap.Config.HARDWARE) {
            return this
        }
        return try {
            BitmapDrawable(resources, bitmap.copy(Bitmap.Config.ARGB_8888, false))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Fills the transition image with the picture we came in with, see plan 18 section 3.3.
     *
     * The snapshot is what the framework captured of the grid tile. Its default implementation
     * makes either an ImageView carrying a drawable or a plain View carrying a background, so both
     * have to be read. See plan 18 section 3.5.
     */
    /**
     * Called when the framework accepts our transition image on the way in.
     *
     * ⚠️ This is also where the pager has to be hidden. The page is laid out at its final size and
     * fully opaque from the very first frame, so a tile growing on top of a picture that is already
     * there reads as two copies of one photo rather than as one photo growing. It is the way in's
     * half of the problem section 3.6 (1) describes for the way out.
     *
     * This runs on the first pre-draw, before anything has been painted.
     */
    fun onEnterSharedElementMapped() {
        hasSharedElement = true
        val transitionImage = transitionImageOrNull ?: return
        isEntering = true
        // Alpha rather than visibility: the page still has to lay out and load while it is hidden,
        // or currentPageContent() would never come back READY and we would never reveal it.
        binding.viewPager.alpha = 0f
        // ⚠️ Being mapped is not a promise that the transition will run. Until onSharedElementStart
        // arrives the pager is hidden and the transition image is still empty, so every frame of
        // waiting is a black screen - give up quickly and let the page show itself.
        scheduleEnterGiveUp(transitionImage, ENTER_START_TIMEOUT_MILLIS)
    }

    private fun scheduleEnterGiveUp(transitionImage: ImageView, delayMillis: Long) {
        transitionImage.removeCallbacks(enterGiveUpRunnable)
        transitionImage.postDelayed(enterGiveUpRunnable, delayMillis)
    }

    private val enterGiveUpRunnable = Runnable { onEnterTransitionEnd() }

    /** The way in is over - called by the framework, or by the timeout above if it never is. */
    fun onEnterTransitionEnd() {
        if (!isEntering) {
            return
        }
        isEntering = false
        transitionImageOrNull?.removeCallbacks(enterGiveUpRunnable)
        logMediaTransition("viewer: revealing pager, fading transition image out")
        hideTransitionImageWhenPageReady()
    }

    fun showTransitionImage(snapshot: View) {
        val transitionImage = transitionImageOrNull ?: return
        val drawable = (snapshot as? ImageView)?.drawable ?: snapshot.background
        if (drawable == null) {
            // The transition is running but we have nothing to cover the pager with, so there is
            // no reason to keep it hidden.
            onEnterTransitionEnd()
            return
        }
        // It really is running now, so allow it the time it needs.
        scheduleEnterGiveUp(transitionImage, ENTER_END_TIMEOUT_MILLIS)
        transitionImage.apply {
            animate().cancel()
            alpha = 1f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
            setImageDrawable(drawable)
        }
    }

    /**
     * Fades the transition image out once the page underneath has something to show, see plan 18
     * section 3.3.
     *
     * Going by the transition alone would blink a blank screen for a page that is still loading.
     */
    fun hideTransitionImageWhenPageReady(attempt: Int = 0) {
        val transitionImage = transitionImageOrNull ?: return
        // Only reached once the way in is over. The transition image still covers the pager
        // opaquely, so bringing it back now is not visible until the fade below uncovers it.
        binding.viewPager.alpha = 1f
        if (transitionImage.drawable == null) {
            return
        }
        val videoReady = currentVideoBinding?.playerView?.isVisible == true
            && renderedVideoPath == currentPath
        if (!videoReady && currentPageContent() is PageContent.Loading
            && attempt < TRANSITION_IMAGE_WAIT_FRAMES) {
            binding.viewPager.doOnPreDraw { hideTransitionImageWhenPageReady(attempt + 1) }
            return
        }
        transitionImage.animate()
            .alpha(0f)
            .setDuration(mediumAnimTime.toLong())
            .withEndAction {
                // Emptied rather than hidden: it has to stay VISIBLE for the return transition to
                // be able to capture it at all. See plan 18 section 3.1.
                transitionImage.setImageDrawable(null)
                transitionImage.alpha = 1f
            }
            .start()
    }

    /**
     * What the page on screen can show right now, see plan 18 section 3.5.
     *
     * Read straight off the views: loading finishes inside MediaViewerAdapter and there is no way
     * out of it, and this is only asked twice in a viewer session.
     */
    private fun currentPageContent(): PageContent =
        when (val holder = pageHolderAt(binding.viewPager.currentItem)) {
            is MediaViewerAdapter.ImageViewHolder -> {
                val itemBinding = holder.binding
                when {
                    itemBinding.errorText.isVisible -> PageContent.Error
                    itemBinding.image.isVisible ->
                        itemBinding.image.drawable
                            ?.let { PageContent.Ready(it) } ?: PageContent.Loading
                    itemBinding.largeImage.isVisible ->
                        if (itemBinding.largeImage.isReady) {
                            largeImageContent(itemBinding.largeImage)
                        } else {
                            PageContent.Loading
                        }
                    else -> PageContent.Loading
                }
            }
            is MediaViewerAdapter.VideoViewHolder -> {
                val itemBinding = holder.binding
                when {
                    itemBinding.errorLayout.isVisible -> PageContent.Error
                    // Playing: the current frame is what the eye is on, and the texture is already
                    // the size of the video rather than of the whole page. Plan 18 D12.
                    itemBinding.playerView.isVisible ->
                        (itemBinding.playerView.videoSurfaceView as? TextureView)?.bitmap
                            ?.let { PageContent.Ready(BitmapDrawable(resources, it)) }
                            ?: PageContent.Loading
                    itemBinding.thumbnailImage.isVisible ->
                        itemBinding.thumbnailImage.drawable
                            ?.let { PageContent.Ready(it) } ?: PageContent.Loading
                    else -> PageContent.Loading
                }
            }
            else -> PageContent.Loading
        }

    /**
     * The visible part of a tiled photo, without its letterbox.
     *
     * Drawing the whole view in would bring the black bars with it, and the framework applies the
     * tile's centerCrop at the far end of the transition - so a landscape photo would jump at the
     * very last moment. PhotoView hands over its original drawable and a TextureView is already
     * the shape of the video, so only this branch has to crop. See plan 18 section 3.5.
     */
    private fun largeImageContent(view: SubsamplingScaleImageView): PageContent {
        val viewWidth = view.width
        val viewHeight = view.height
        if (viewWidth <= 0 || viewHeight <= 0) {
            return PageContent.Loading
        }
        val orientation = view.appliedOrientation
        val rotated90Or270 = orientation == SubsamplingScaleImageView.ORIENTATION_90
            || orientation == SubsamplingScaleImageView.ORIENTATION_270
        val imageWidth = if (rotated90Or270) view.sHeight else view.sWidth
        val imageHeight = if (rotated90Or270) view.sWidth else view.sHeight
        if (imageWidth <= 0 || imageHeight <= 0) {
            return PageContent.Loading
        }
        // We only get here at the minimum scale - returnDrawable() turns a zoomed page away - so
        // the picture is fitted and centred, and that is all it takes to find it.
        val scale = view.scale
        val width = (imageWidth * scale).roundToInt().coerceIn(1, viewWidth)
        val height = (imageHeight * scale).roundToInt().coerceIn(1, viewHeight)
        val left = (viewWidth - width) / 2
        val top = (viewHeight - height) / 2
        val bitmap = try {
            Bitmap.createBitmap(view.drawToBitmap(), left, top, width, height)
        } catch (e: Exception) {
            e.printStackTrace()
            return PageContent.Error
        }
        return PageContent.Ready(BitmapDrawable(resources, bitmap))
    }

    /** The page on screen, or null when it has no view right now. */
    private fun currentPageRoot(): SwipeDownDismissLayout? =
        pageHolderAt(binding.viewPager.currentItem)?.itemView as? SwipeDownDismissLayout

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
        (activity as? MediaViewerActivity)?.viewportSessionId?.let {
            MediaViewerViewportCoordinator.unregisterViewer(it, viewportListener)
        }
        viewportStatus = null
        if (returnState != ReturnState.RETURNING) {
            (activity as? MediaViewerActivity)?.setViewerBackgroundAlpha(1f)
        }
        // PixelCopy can still be in flight when rotation recreates the view. Its completion is
        // tied to the old view and intentionally ignored; let the new view accept another return.
        if (returnState == ReturnState.PREPARING_RETURN) {
            returnState = ReturnState.IDLE
        }
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
        renderedVideoPath = null
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
    private fun videoHolderAt(position: Int): MediaViewerAdapter.VideoViewHolder? =
        pageHolderAt(position) as? MediaViewerAdapter.VideoViewHolder

    private fun pageHolderAt(position: Int): RecyclerView.ViewHolder? {
        val recyclerView = binding.viewPager.getChildAt(0) as? RecyclerView ?: return null
        return recyclerView.findViewHolderForAdapterPosition(position)
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
            renderedVideoPath = playerHolder?.currentPath
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
            publishCurrentViewportIfIdle()
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
        private const val BACKGROUND_FULL_REVEAL_FRACTION = 0.125f
        private const val BACKGROUND_ALPHA_AT_DRAG_START = 0.85f

        // Spec 11 section 6.3. 0.25 is there to slow fast motion down, e.g. a golf swing.
        private val PLAYBACK_SPEEDS = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f)

        /**
         * How many frames to wait for the page under the entering picture, see plan 18 section 3.3.
         *
         * A photo off the disk arrives within a couple of frames; anything slower has to give up
         * rather than leave the picture stuck on top of the pager.
         */
        private const val TRANSITION_IMAGE_WAIT_FRAMES = 30

        /** How long to wait for a mapped transition to actually start before giving up on it. */
        private const val ENTER_START_TIMEOUT_MILLIS = 300L

        /** How long a started transition may run before we stop waiting for its end. */
        private const val ENTER_END_TIMEOUT_MILLIS = 1500L

        private val SPEED_ITEM_IDS = intArrayOf(
            R.id.action_speed_0_25, R.id.action_speed_0_5, R.id.action_speed_0_75,
            R.id.action_speed_1, R.id.action_speed_1_5, R.id.action_speed_2
        )
    }

    @Parcelize
    class Args(val intent: Intent, val position: Int) : ParcelableArgs

    @Parcelize
    private class State(val paths: @WriteWith<ParcelableListParceler> List<Path>) : ParcelableState

    private enum class ReturnState {
        IDLE,
        PREPARING_RETURN,
        RETURNING
    }
}
