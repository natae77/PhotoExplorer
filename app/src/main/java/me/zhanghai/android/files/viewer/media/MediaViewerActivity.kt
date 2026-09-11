/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.content.Intent
import android.os.Bundle
import android.transition.Transition
import android.transition.TransitionListenerAdapter
import android.view.View
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.core.app.SharedElementCallback
import androidx.fragment.app.commit
import java8.nio.file.Path
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.util.extraPathList
import me.zhanghai.android.files.util.putArgs

class MediaViewerActivity : AppActivity() {
    private var fragment: MediaViewerFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        window.sharedElementEnterTransition?.addListener(object : TransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition) {
                transition.removeListener(this)
                logMediaTransition("viewer: enter transition end")
                fragment?.onEnterTransitionEnd()
            }
        })
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

        fun putExtras(intent: Intent, paths: List<Path>, position: Int) {
            // All extra put here must be framework classes, or we may crash the resolver activity.
            intent.extraPathList = paths
            intent.putExtra(EXTRA_POSITION, position)
        }
    }
}
