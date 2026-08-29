/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.os.Handler
import android.os.Looper
import android.view.View
import me.zhanghai.android.files.util.fadeInUnsafe
import me.zhanghai.android.files.util.fadeOutUnsafe

private const val DELAY_MILLIS = 500L

/**
 * Shows [view] only when the wait actually lasts, see spec 11a section 6.1.
 *
 * A video page waits for two things that overlap: its thumbnail and the player's buffering. Both
 * report here, and the indicator stays up until both are done, so a finished thumbnail cannot hide
 * a still buffering player.
 */
class DelayedProgress(private val view: View) {
    enum class Reason { THUMBNAIL, BUFFERING }

    private val handler = Handler(Looper.getMainLooper())
    private val reasons = mutableSetOf<Reason>()
    private val showRunnable = Runnable { view.fadeInUnsafe(true) }

    fun begin(reason: Reason) {
        // Already waiting: the pending show still covers this reason.
        if (!reasons.add(reason) || reasons.size > 1) {
            return
        }
        handler.postDelayed(showRunnable, DELAY_MILLIS)
    }

    fun end(reason: Reason) {
        if (!reasons.remove(reason) || reasons.isNotEmpty()) {
            return
        }
        hide()
    }

    /** For recycling and errors, when neither reason is worth tracking any more. */
    fun endAll() {
        reasons.clear()
        hide()
    }

    private fun hide() {
        handler.removeCallbacks(showRunnable)
        view.fadeOutUnsafe()
    }
}
