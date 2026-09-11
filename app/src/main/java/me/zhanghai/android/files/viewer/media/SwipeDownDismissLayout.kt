/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import kotlin.math.abs

/**
 * A viewer page that follows the finger downwards and closes the viewer when let go, see doc 10
 * section 4.
 *
 * The page moves with the finger so that the gesture shows its own progress, and it is released the
 * way ViewPager2 releases a horizontal swipe: far enough, or fast enough.
 */
class SwipeDownDismissLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    /**
     * Whether a downward drag belongs to us. False while the content wants it for itself, which
     * today means a zoomed image being panned.
     */
    var canDismiss: () -> Boolean = { true }

    var listener: Listener? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity

    private var downRawX = 0f
    private var downRawY = 0f
    private var isDragging = false
    private var gestureRejected = false
    private var velocityTracker: VelocityTracker? = null

    /** Puts the page back where it belongs, for a view about to be reused. */
    fun reset() {
        animate().cancel()
        isDragging = false
        gestureRejected = false
        recycleVelocityTracker()
        translationY = 0f
        scaleX = 1f
        scaleY = 1f
        alpha = 1f
    }

    /**
     * SubsamplingScaleImageView asks its parent to keep out of the way whenever it takes a touch.
     * Honour that only while it really needs the gesture - when it is zoomed - or we would never
     * get to see a drag on it at all. PhotoView and PlayerView never ask.
     */
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && !isDragging && canDismiss()) {
            return
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    /** Whether this move turns into a drag of ours. Dominantly downwards, and nothing else wants it. */
    private fun shouldStartDrag(event: MotionEvent): Boolean {
        if (isDragging || gestureRejected || event.pointerCount != 1 || !canDismiss()) {
            return false
        }
        val offsetY = event.rawY - downRawY
        val offsetX = event.rawX - downRawX
        // Dominantly downwards, so that paging left and right is left alone.
        return offsetY > touchSlop && offsetY > abs(offsetX) * DIRECTION_RATIO
    }

    private fun startDrag() {
        isDragging = true
        // Now that it is ours, keep ViewPager2 from taking it back.
        parent?.requestDisallowInterceptTouchEvent(true)
        listener?.onDragStarted(this)
    }

    private fun rejectGesture() {
        gestureRejected = true
        recycleVelocityTracker()
        if (isDragging) {
            isDragging = false
            listener?.onDragCancelled(this)
            animateBack()
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                isDragging = false
                gestureRejected = false
                recycleVelocityTracker()
                velocityTracker = VelocityTracker.obtain()
                trackVelocity(event)
            }
            MotionEvent.ACTION_MOVE -> {
                trackVelocity(event)
                if (shouldStartDrag(event)) {
                    startDrag()
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> rejectGesture()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> recycleVelocityTracker()
        }
        return false
    }

    /**
     * ⚠️ The drag can also have to start here.
     *
     * A video page has no child that takes the touch - the player view does not use its own
     * controller and the thumbnail is a plain image - so the page itself ends up consuming the
     * down event as a click target. When that happens there is no touch target below us, and
     * Android stops calling [onInterceptTouchEvent] for the rest of the gesture and delivers the
     * moves straight here.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            rejectGesture()
            return true
        }
        if (gestureRejected) {
            // Do not turn the end of a rejected multi-touch stream into a click or a new drag.
            if (event.actionMasked == MotionEvent.ACTION_UP
                || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                recycleVelocityTracker()
            }
            return true
        }
        trackVelocity(event)
        if (!isDragging) {
            if (event.actionMasked == MotionEvent.ACTION_MOVE && shouldStartDrag(event)) {
                startDrag()
            } else {
                if (event.actionMasked == MotionEvent.ACTION_UP
                    || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    recycleVelocityTracker()
                }
                // Let the click through, among other things.
                return super.onTouchEvent(event)
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE ->
                // Once the direction is known, catch up the whole distance accumulated while the
                // gesture was below the threshold, then stay exactly under the finger.
                setDragOffset((event.rawY - downRawY).coerceAtLeast(0f))
            MotionEvent.ACTION_UP -> {
                // Keep the shared-element handoff aligned with the finger's final position even
                // when UP arrives after the last rendered MOVE.
                val offset = (event.rawY - downRawY).coerceAtLeast(0f)
                setDragOffset(offset)
                val velocity = velocityTracker?.let {
                    it.computeCurrentVelocity(VELOCITY_UNITS)
                    it.yVelocity
                } ?: 0f
                isDragging = false
                recycleVelocityTracker()
                val isFarEnough = offset >= height * DISMISS_FRACTION
                val isFastEnough = velocity >= minimumFlingVelocity
                    && offset >= height * FLICK_MIN_FRACTION
                if (isFarEnough || isFastEnough) {
                    // Leave the page where it is, the activity exit animation takes it from here.
                    listener?.onDismissed(this)
                } else {
                    listener?.onDragCancelled(this)
                    animateBack()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                recycleVelocityTracker()
                listener?.onDragCancelled(this)
                animateBack()
            }
        }
        return true
    }

    private fun setDragOffset(offset: Float) {
        translationY = offset
        val progress = if (height > 0) (offset / height).coerceIn(0f, 1f) else 0f
        val scale = 1f - MAX_SCALE_DOWN * progress
        scaleX = scale
        scaleY = scale
        // Keep the media opaque. The shared element used for the return is opaque too, so fading
        // here would cause a brightness jump on the frame where the return transition starts.
        alpha = 1f
        listener?.onDragProgress(this, progress)
    }

    private fun animateBack() {
        animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(ANIMATE_BACK_DURATION)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }

    /**
     * ⚠️ Feeds the tracker screen coordinates rather than the event's own.
     *
     * The page moves with the finger, so within the page the finger hardly moves at all and the
     * velocity would always come out near zero.
     */
    private fun trackVelocity(event: MotionEvent) {
        val tracker = velocityTracker ?: return
        val copy = MotionEvent.obtain(event)
        copy.setLocation(event.rawX, event.rawY)
        tracker.addMovement(copy)
        copy.recycle()
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    companion object {
        // How far down the page has to be before letting go closes the viewer.
        private const val DISMISS_FRACTION = 0.25f
        // How far a flick has to have gone before its speed counts for anything.
        private const val FLICK_MIN_FRACTION = 0.1f
        // How much more vertical than horizontal a drag has to be before it becomes ours.
        private const val DIRECTION_RATIO = 1.5f
        private const val MAX_SCALE_DOWN = 0.2f
        private const val ANIMATE_BACK_DURATION = 200L
        // Pixels per second, matching ViewConfiguration's minimum fling velocity.
        private const val VELOCITY_UNITS = 1000
    }

    interface Listener {
        fun onDragStarted(layout: SwipeDownDismissLayout) {}
        fun onDragProgress(layout: SwipeDownDismissLayout, progress: Float) {}
        fun onDragCancelled(layout: SwipeDownDismissLayout) {}
        fun onDismissed(layout: SwipeDownDismissLayout)
    }
}
