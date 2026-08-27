/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.compat.PreferenceManagerCompat

/**
 * Clears every per-folder view type and sort setting at once, see spec 7.2.
 *
 * All of them live in one separate SharedPreferences file (`PathSettings.NAME_SUFFIX`), so a single
 * `clear()` is enough.
 */
class ResetPathSettingsPreference : Preference {
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

    override fun onClick() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_reset_path_settings_title)
            .setMessage(R.string.settings_reset_path_settings_message)
            .setPositiveButton(R.string.reset) { _, _ -> reset() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun reset() {
        val name = "${PreferenceManagerCompat.getDefaultSharedPreferencesName(application)}_" +
            PathSettings.NAME_SUFFIX
        val mode = PreferenceManagerCompat.defaultSharedPreferencesMode
        application.getSharedPreferences(name, mode).edit().clear().apply()
    }
}
