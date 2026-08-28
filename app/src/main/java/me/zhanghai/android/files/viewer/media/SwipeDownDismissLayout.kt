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

    var onDismiss: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity

    private var downRawX = 0f
    private var downRawY = 0f
    private var isDragging = false
    private var velocityTracker: VelocityTracker? = null

    /** Puts the page back where it belongs, for a view about to be reused. */
    fun reset() {
        animate().cancel()
        isDragging = false
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
        if (isDragging || event.pointerCount != 1 || !canDismiss()) {
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
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                isDragging = false
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
                // Take the slop off, or the page would jump by that much when the drag starts.
                setDragOffset((event.rawY - downRawY - touchSlop).coerceAtLeast(0f))
            MotionEvent.ACTION_UP -> {
                val offset = translationY
                val velocity = velocityTracker?.let {
                    it.computeCurrentVelocity(VELOCITY_UNITS)
                    it.yVelocity
                } ?: 0f
                isDragging = false
                recycleVelocityTracker()
                // The rule ViewPager2 uses for a horizontal swipe: far enough, or fast enough.
                // The flick also has to have gone somewhere, or a slow short slide would count -
                // ViewConfiguration's minimum fling velocity is only 50dp/s.
                val isFarEnough = offset >= height * DISMISS_FRACTION
                val isFastEnough = velocity >= minimumFlingVelocity
                    && offset >= height * FLICK_MIN_FRACTION
                if (isFarEnough || isFastEnough) {
                    // Leave the page where it is, the activity exit animation takes it from here.
                    onDismiss?.invoke()
                } else {
                    animateBack()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                recycleVelocityTracker()
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
        alpha = 1f - MAX_FADE * progress
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
        // How far down the page has to be before letting go closes the viewer. ViewPager2 uses half
        // a page for a horizontal swipe, but that is a page coming in rather than one going out.
        private const val DISMISS_FRACTION = 0.25f
        // How far a flick has to have gone before its speed counts for anything.
        private const val FLICK_MIN_FRACTION = 0.1f
        // How much more vertical than horizontal a drag has to be before it becomes ours.
        private const val DIRECTION_RATIO = 1.5f
        private const val MAX_SCALE_DOWN = 0.2f
        private const val MAX_FADE = 0.5f
        private const val ANIMATE_BACK_DURATION = 200L
        // Pixels per second, matching ViewConfiguration's minimum fling velocity.
        private const val VELOCITY_UNITS = 1000
    }
}
