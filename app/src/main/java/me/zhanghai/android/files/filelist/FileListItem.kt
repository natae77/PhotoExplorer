/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import me.zhanghai.android.files.file.FileItem

/**
 * What the file list adapter holds. Media view mode inserts date tiles between files, and a date
 * tile is not a file.
 *
 * A fake [FileItem] with a non-existent path would be the other option, but then something without
 * real attributes would travel through every code path that assumes a file, and missing a single
 * branch in opening, selecting, sorting or the item menu would be a crash. Splitting the type makes
 * the compiler point at each place that has to decide.
 */
sealed interface FileListItem {
    data class File(val file: FileItem) : FileListItem

    /** [epochMillis] is midnight of that day in the device time zone. */
    data class Date(val epochMillis: Long) : FileListItem
}
