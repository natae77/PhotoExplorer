/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import com.google.android.material.chip.Chip
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.BookmarkDirectoryChipBinding
import me.zhanghai.android.files.navigation.BookmarkDirectory
import me.zhanghai.android.files.util.getDimensionPixelSize
import me.zhanghai.android.files.util.layoutInflater

class BookmarkBarLayout : HorizontalScrollView {
    private val itemsLayout: LinearLayout
    private val chips = mutableMapOf<Long, Chip>()
    private var bookmarkDirectories: List<BookmarkDirectory> = emptyList()
    private var currentPath: Path? = null
    private lateinit var listener: Listener

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        isHorizontalScrollBarEnabled = false
        itemsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val horizontalPadding =
                context.getDimensionPixelSize(R.dimen.bookmark_bar_padding_horizontal)
            setPaddingRelative(horizontalPadding, 0, horizontalPadding, 0)
        }
        addView(itemsLayout, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun setBookmarkDirectories(bookmarkDirectories: List<BookmarkDirectory>) {
        if (this.bookmarkDirectories == bookmarkDirectories) {
            return
        }
        this.bookmarkDirectories = bookmarkDirectories
        rebuildChips()
    }

    fun setCurrentPath(path: Path) {
        currentPath = path
        updateCheckedStates()
    }

    private fun rebuildChips() {
        itemsLayout.removeAllViews()
        chips.clear()
        val spacing = context.getDimensionPixelSize(R.dimen.bookmark_chip_spacing)
        for ((index, bookmarkDirectory) in bookmarkDirectories.withIndex()) {
            val chip = BookmarkDirectoryChipBinding.inflate(
                context.layoutInflater, itemsLayout, false
            ).root
            chip.text = bookmarkDirectory.name
            chip.setOnClickListener {
                updateCheckedStates()
                if (currentPath != bookmarkDirectory.path) {
                    listener.navigateTo(bookmarkDirectory)
                }
            }
            chip.setOnLongClickListener {
                listener.edit(bookmarkDirectory)
                true
            }
            if (index != bookmarkDirectories.lastIndex) {
                (chip.layoutParams as LinearLayout.LayoutParams).marginEnd = spacing
            }
            chips[bookmarkDirectory.id] = chip
            itemsLayout.addView(chip)
        }
        visibility = if (bookmarkDirectories.isEmpty()) View.GONE else View.VISIBLE
        updateCheckedStates()
    }

    private fun updateCheckedStates() {
        for (bookmarkDirectory in bookmarkDirectories) {
            chips[bookmarkDirectory.id]?.isChecked = currentPath == bookmarkDirectory.path
        }
    }

    interface Listener {
        fun navigateTo(bookmarkDirectory: BookmarkDirectory)
        fun edit(bookmarkDirectory: BookmarkDirectory)
    }
}
