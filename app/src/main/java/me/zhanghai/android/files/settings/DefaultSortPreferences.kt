/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import me.zhanghai.android.files.filelist.FileSortOptions
import me.zhanghai.android.files.util.valueCompat
import rikka.preference.SimpleMenuPreference

/**
 * The global default sort options.
 *
 * Sort options are stored per folder now (spec 7.2), which means the file list menu no longer
 * writes the global value at all. Without these two entries the global default would become
 * unreachable.
 *
 * [FileSortOptions] is a single `@Parcelize`d value rather than separate preference keys, so these
 * intercept the persistence calls of [SimpleMenuPreference] instead of using its own key. The
 * stored form is the enum ordinal as a string, matching how `EnumSettingLiveData` writes enums.
 *
 * The preference key is a placeholder that nothing is ever stored under, so `android:defaultValue`
 * has to be present in XML: without it `dispatchSetInitialValue()` skips `onSetInitialValue()`
 * entirely for a key the SharedPreferences does not contain, the value stays null and the summary
 * reads "Not set".
 */
class DefaultSortByPreference : SimpleMenuPreference {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun getPersistedString(defaultReturnValue: String?): String =
        Settings.FILE_LIST_SORT_OPTIONS.valueCompat.by.ordinal.toString()

    override fun persistString(value: String?): Boolean {
        val ordinal = value?.toIntOrNull() ?: return false
        val by = FileSortOptions.By.entries.getOrNull(ordinal) ?: return false
        Settings.FILE_LIST_SORT_OPTIONS.putValue(
            Settings.FILE_LIST_SORT_OPTIONS.valueCompat.copy(by = by)
        )
        return true
    }
}

class DefaultSortOrderPreference : SimpleMenuPreference {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun getPersistedString(defaultReturnValue: String?): String =
        Settings.FILE_LIST_SORT_OPTIONS.valueCompat.order.ordinal.toString()

    override fun persistString(value: String?): Boolean {
        val ordinal = value?.toIntOrNull() ?: return false
        val order = FileSortOptions.Order.entries.getOrNull(ordinal) ?: return false
        Settings.FILE_LIST_SORT_OPTIONS.putValue(
            Settings.FILE_LIST_SORT_OPTIONS.valueCompat.copy(order = order)
        )
        return true
    }
}
