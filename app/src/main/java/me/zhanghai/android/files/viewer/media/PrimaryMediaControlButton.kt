/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/** A full, non-overlapping touch/accessibility cell around an unchanged 52 dp visual button. */
class PrimaryMediaControlButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    init {
        isClickable = true
        isFocusable = true
    }

    override fun getAccessibilityClassName(): CharSequence = android.widget.Button::class.java.name

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true

    // Disabled cells still consume the gesture so it cannot fall through and hide all controls.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (isEnabled) super.dispatchTouchEvent(event) else true
}
