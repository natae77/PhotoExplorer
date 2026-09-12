/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.transition.Transition
import android.transition.TransitionListenerAdapter
import android.view.View
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.core.app.SharedElementCallback
import androidx.fragment.app.commit
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.util.extraPathList
import me.zhanghai.android.files.util.putArgs

class MediaViewerActivity : AppActivity() {
    private var fragment: MediaViewerFragment? = null
    private val viewerBackground = ColorDrawable(Color.BLACK)
    private var viewerBackgroundAnimator: ValueAnimator? = null
    private var hasFinishedEnterTransition = false

    val canRevealFileList: Boolean
        get() = intent.getBooleanExtra(EXTRA_CAN_REVEAL_FILE_LIST, false)

    val viewportSessionId: String?
        get() = intent.getStringExtra(EXTRA_VIEWPORT_SESSION_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The window stays black from the first frame, including external VIEW launches. Internal
        // downward dismissal changes this drawable's alpha to reveal FileListActivity underneath.
        window.setBackgroundDrawable(viewerBackground)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        fragment = if (savedInstanceState == null) {
            val intent = intent
            val position = intent.getIntExtra(EXTRA_POSITION, 0)
            MediaViewerFragment()
                .putArgs(MediaViewerFragment.Args(intent, position))
                .also { supportFragmentManager.commit { add(android.R.id.content, it) } }
        } else {
            supportFragmentManager.findFragmentById(android.R.id.content) as? MediaViewerFragment
        }
        // ⚠️ Has to happen here, not from the fragment. EnterTransitionCoordinator grabs this
        // listener in its constructor, and it is built right after onCreate() - by the time
        // Fragment.onActivityCreated() runs (FragmentActivity dispatches it from onStart()) the
        // coordinator already exists and would never see it. The view is found late instead, from
        // inside the callback, which is only called after the first pre-draw. See plan 18 3.2.2.
        ActivityCompat.setEnterSharedElementCallback(this, sharedElementCallback)
        // ⚠️ SharedElementCallback.onSharedElementEnd() is NOT the end of the transition - the
        // framework calls it before the animation runs, once the end state has been laid out, so
        // that a listener can measure it. Revealing the pager there uncovers the picture at its
        // final size while the tile is still on its way, which is the double image all over again.
        // Plan 18 section 3.3 names the wrong hook; this is the one that means "finished".
        //
        // PhoneWindow inflates this transition per window, so the listener cannot outlive us.
        val enterTransition = window.sharedElementEnterTransition
        if (enterTransition != null) {
            enterTransition.addListener(object : TransitionListenerAdapter() {
                override fun onTransitionEnd(transition: Transition) {
                    transition.removeListener(this)
                    finishEnterTransition("end")
                }

                override fun onTransitionCancel(transition: Transition) {
                    transition.removeListener(this)
                    finishEnterTransition("cancel")
                }
            })
        } else {
            window.decorView.post { finishEnterTransition("none") }
        }
    }

    private fun finishEnterTransition(reason: String) {
        if (hasFinishedEnterTransition) return
        hasFinishedEnterTransition = true
        logMediaTransition("viewer: enter transition $reason")
        viewportSessionId?.let(MediaViewerViewportCoordinator::notifyViewerEnterFinished)
        fragment?.onEnterTransitionEnd()
    }

    /**
     * The way out through the toolbar arrow, see plan 18 D17.
     *
     * AppActivity.onSupportNavigateUp() calls finish(), which does not run a return transition at
     * all. Everything has to leave through the one hook in the fragment instead.
     */
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    /**
     * Closes a viewer that has no shared element using the app's former activity animation.
     *
     * The viewer window became translucent so swipe-down can reveal the file list underneath.
     * Android does not apply its normal close animation to that translucent window, so the
     * previous file list no longer slides in from the left unless we request it explicitly.
     */
    fun finishWithPlainAnimation() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(
            R.anim.media_viewer_close_enter,
            R.anim.media_viewer_close_exit
        )
    }

    fun setViewerBackgroundAlpha(alpha: Float) {
        viewerBackgroundAnimator?.cancel()
        viewerBackgroundAnimator = null
        viewerBackground.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
    }

    fun restoreViewerBackground() {
        viewerBackgroundAnimator?.cancel()
        viewerBackgroundAnimator = ValueAnimator.ofInt(viewerBackground.alpha, 255).apply {
            duration = BACKGROUND_RESTORE_DURATION_MILLIS
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { viewerBackground.alpha = it.animatedValue as Int }
            start()
        }
    }

    fun cancelViewerBackgroundAnimation() {
        viewerBackgroundAnimator?.cancel()
        viewerBackgroundAnimator = null
    }

    override fun onDestroy() {
        viewerBackgroundAnimator?.cancel()
        viewerBackgroundAnimator = null
        if (isFinishing) {
            viewportSessionId?.let(MediaViewerViewportCoordinator::endSession)
        }
        super.onDestroy()
    }

    private val sharedElementCallback = object : SharedElementCallback() {
        override fun onMapSharedElements(
            names: MutableList<String>,
            sharedElements: MutableMap<String, View>
        ) {
            val fragment = fragment ?: return
            val transitionImage = fragment.transitionImageOrNull ?: return
            // ⚠️ Only on the way out. Coming in, onMapSharedElements() runs long before
            // onSharedElementStart() has anything to put in the image, so an unguarded check here
            // would open the viewer with no shared element at all. See plan 18 3.2.1 and D20.
            if (fragment.isReturning && transitionImage.drawable == null) {
                // Nothing to send - do not fly an empty rectangle into the tile (F4, F5).
                logMediaTransition("viewer map: returning, no drawable -> blocked")
                names.clear()
                sharedElements.clear()
                return
            }
            // The viewer's own view carries no transitionName (D15): the name that came in with
            // the ActivityOptions stays the one both sides pair on, all the way to the return.
            val name = names.firstOrNull()
            logMediaTransition(
                "viewer map: returning=${fragment.isReturning} names=$names -> " +
                    if (name != null) "mapped" else "no name"
            )
            if (name == null) {
                return
            }
            if (!fragment.isReturning) {
                fragment.onEnterSharedElementMapped()
            }
            sharedElements[name] = transitionImage
        }

        override fun onSharedElementStart(
            sharedElementNames: MutableList<String>,
            sharedElements: MutableList<View>,
            sharedElementSnapshots: MutableList<View>
        ) {
            val fragment = fragment ?: return
            if (fragment.isReturning) {
                return
            }
            val snapshot = sharedElementSnapshots.firstOrNull()
            logMediaTransition(
                "viewer start: snapshot=" + when {
                    snapshot == null -> "none"
                    snapshot is ImageView && snapshot.drawable != null -> "ImageView.drawable"
                    snapshot.background != null -> "View.background"
                    else -> "empty ${snapshot.javaClass.simpleName}"
                }
            )
            if (snapshot == null) {
                return
            }
            fragment.showTransitionImage(snapshot)
        }

    }

    companion object {
        private val EXTRA_POSITION = "${MediaViewerActivity::class.java.name}.extra.POSITION"
        private val EXTRA_CAN_REVEAL_FILE_LIST =
            "${MediaViewerActivity::class.java.name}.extra.CAN_REVEAL_FILE_LIST"
        private val EXTRA_VIEWPORT_SESSION_ID =
            "${MediaViewerActivity::class.java.name}.extra.VIEWPORT_SESSION_ID"
        private const val BACKGROUND_RESTORE_DURATION_MILLIS = 200L

        fun putExtras(intent: Intent, paths: List<Path>, position: Int) {
            // All extra put here must be framework classes, or we may crash the resolver activity.
            intent.extraPathList = paths
            intent.putExtra(EXTRA_POSITION, position)
        }

        fun markOpenedFromFileList(intent: Intent, viewportSessionId: String) {
            intent.putExtra(EXTRA_CAN_REVEAL_FILE_LIST, true)
            intent.putExtra(EXTRA_VIEWPORT_SESSION_ID, viewportSessionId)
        }
    }
}
