/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import java8.nio.file.Path
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.guessFromPath
import me.zhanghai.android.files.file.isVideo
import me.zhanghai.android.files.provider.document.isDocumentPath
import me.zhanghai.android.files.provider.linux.isLinuxPath

/**
 * Whether this path is a video we can play inside the app, see spec 11 section 3.
 *
 * The MIME type is guessed from the file name only. getItemViewType() calls this on the main
 * thread for every page, so it must never touch the file system.
 */
val Path.isPlayableVideo: Boolean
    get() = MimeType.guessFromPath(toString()).isVideo && (isLinuxPath || isDocumentPath)
