/*
 * Copyright (c) 2023 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

enum class FileViewType {
    LIST,
    GRID,

    // Must stay last: this is persisted to SharedPreferences by ordinal, so inserting a value in
    // the middle would silently change what existing users have stored. See spec 9.
    MEDIA
}
