/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.graphics.Canvas
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.getDimensionPixelSize
import me.zhanghai.android.files.util.getDrawableByAttr
import kotlin.math.roundToInt

class FileListDividerItemDecoration(
    context: Context,
    private val adapter: FileListAdapter
) : RecyclerView.ItemDecoration() {
    private val divider = context.getDrawableByAttr(android.R.attr.listDivider)
    private val dividerHeight = context.getDimensionPixelSize(R.dimen.horizontal_divider_height)
    private val startMargin = context.getDimensionPixelSize(R.dimen.content_start_margin)
    private val endMargin = context.getDimensionPixelSize(R.dimen.screen_edge_margin)

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (adapter.viewType != FileViewType.LIST) {
            return
        }
        val isRtl = parent.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val left = if (isRtl) endMargin else startMargin
        val right = parent.width - if (isRtl) startMargin else endMargin
        for (index in 0..<parent.childCount) {
            val child = parent.getChildAt(index)
            if (!adapter.isDirectoryChild(parent, child)) {
                continue
            }
            val bottom = (child.bottom + child.translationY).roundToInt()
            divider.setBounds(left, bottom - dividerHeight, right, bottom)
            divider.draw(canvas)
        }
    }
}
