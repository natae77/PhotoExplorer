/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.compat

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import androidx.annotation.IdRes
import androidx.core.view.ViewCompat
import me.zhanghai.android.foregroundcompat.ForegroundCompat
import me.zhanghai.android.files.hiddenapi.RestrictedHiddenApi
import me.zhanghai.android.files.util.lazyReflectedMethod

@RestrictedHiddenApi
private val setTransitionAlphaMethod by lazyReflectedMethod(
    View::class.java, "setTransitionAlpha", Float::class.java
)

@RestrictedHiddenApi
private val setTransitionVisibilityMethod by lazyReflectedMethod(
    View::class.java, "setTransitionVisibility", Int::class.java
)

@Suppress("UNCHECKED_CAST")
fun <T : View> View.requireViewByIdCompat(@IdRes id: Int): T =
    ViewCompat.requireViewById(this, id) as T

var View.scrollIndicatorsCompat: Int
    get() = ViewCompat.getScrollIndicators(this)
    set(value) {
        ViewCompat.setScrollIndicators(this, value)
    }

fun View.setScrollIndicatorsCompat(indicators: Int, mask: Int) {
    ViewCompat.setScrollIndicators(this, indicators, mask)
}

fun View.setTransitionAlphaCompat(alpha: Float) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        transitionAlpha = alpha
    } else {
        setTransitionAlphaMethod.invoke(this, alpha)
    }
}

fun View.setTransitionVisibilityCompat(visibility: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        setTransitionVisibility(visibility)
    } else {
        setTransitionVisibilityMethod.invoke(this, visibility)
    }
}

var View.foregroundCompat: Drawable?
    get() = ForegroundCompat.getForeground(this)
    set(value) {
        ForegroundCompat.setForeground(this, value)
    }

var View.foregroundGravityCompat: Int
    get() = ForegroundCompat.getForegroundGravity(this)
    set(value) {
        ForegroundCompat.setForegroundGravity(this, value)
    }

var View.foregroundTintListCompat: ColorStateList?
    get() = ForegroundCompat.getForegroundTintList(this)
    set(value) {
        ForegroundCompat.setForegroundTintList(this, value)
    }

var View.foregroundTintModeCompat: PorterDuff.Mode?
    get() = ForegroundCompat.getForegroundTintMode(this)
    set(value) {
        ForegroundCompat.setForegroundTintMode(this, value)
    }
