/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.graphics.drawable.Drawable
import android.util.Log
import java8.nio.file.Path
import me.zhanghai.android.files.BuildConfig

private const val LOG_TAG = "MediaTransition"

/**
 * ⚠️ Plan 14 section 5.1: a transition that never starts, one that starts with no shared element,
 * and one that starts with an empty picture all look the same on screen and have nothing to do with
 * each other. These lines are the only way to tell them apart.
 */
fun logMediaTransition(message: String) {
    if (BuildConfig.DEBUG) {
        Log.d(LOG_TAG, message)
    }
}

/**
 * The shared element name of the media tile for [path], see plan 14 section 3.2.3.
 *
 * The name has to survive a trip through ActivityOptions and back, and both sides only ever know
 * the file, so the path string is all it needs to be.
 */
fun mediaTransitionName(path: Path): String = "media:$path"

/**
 * What the page on screen is able to hand to the return transition, see plan 14 section 3.5.
 *
 * Three callers ask the same question - the picture to fly back with, when the entering picture may
 * be faded out, and whether there is anything to transition at all - so they all ask it here.
 */
sealed class PageContent {
    class Ready(val drawable: Drawable) : PageContent()

    object Loading : PageContent()

    object Error : PageContent()
}
