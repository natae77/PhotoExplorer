/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.dpToDimensionPixelSize

private const val SCRIM_SIZE_DP = 40
private const val ICON_SIZE_DP = 24

/**
 * Puts a translucent disc behind an app bar icon, see spec 11a section 3.3.
 *
 * The app bar has no background of its own any more, so a white icon disappears over a bright
 * photo. Wrapping keeps whatever icon the theme picked instead of hard coding one of ours.
 */
fun Drawable.withCircleScrim(context: Context): Drawable {
    val scrimSize = context.dpToDimensionPixelSize(SCRIM_SIZE_DP)
    val inset = (scrimSize - context.dpToDimensionPixelSize(ICON_SIZE_DP)) / 2
    val scrim = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(context.getColor(R.color.dark_50_percent))
        setSize(scrimSize, scrimSize)
    }
    return LayerDrawable(arrayOf(scrim, this)).apply { setLayerInset(1, inset, inset, inset, inset) }
}
