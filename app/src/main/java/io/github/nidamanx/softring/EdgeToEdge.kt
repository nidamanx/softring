// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets

/**
 * Draw behind the system bars on every Android version (it is mandatory from
 * Android 15) and keep the content out of them. On wide screens (tablets) the
 * content is centered with a maximum width, so lines stay readable.
 */
internal object EdgeToEdge {

    private const val MAX_CONTENT_WIDTH_DP = 600

    fun apply(activity: Activity, root: ViewGroup) {
        val window = activity.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }

        val base = intArrayOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        root.setOnApplyWindowInsetsListener { view, insets ->
            val (left, top, right, bottom) = systemBarInsets(insets)
            // Top as a margin: the page scrolls below the status bar, not behind it.
            // Bottom as padding: with clipToPadding="false" it scrolls behind the
            // navigation bar, and the last line can still be scrolled above it.
            view.layoutParams = (view.layoutParams as ViewGroup.MarginLayoutParams).apply { topMargin = top }
            view.setPadding(base[0] + left, base[1], base[2] + right, base[3] + bottom)
            insets
        }
        root.requestApplyInsets()

        // A window size change (rotation, split screen) recreates the activity,
        // so the width from the configuration is enough
        val content = root.getChildAt(0)
        val density = activity.resources.displayMetrics.density
        val windowWidth = (activity.resources.configuration.screenWidthDp * density).toInt()
        val maxWidth = (MAX_CONTENT_WIDTH_DP * density).toInt() + content.paddingLeft + content.paddingRight
        val extra = ((windowWidth - maxWidth) / 2).coerceAtLeast(0)
        content.setPadding(
            content.paddingLeft + extra, content.paddingTop, content.paddingRight + extra, content.paddingBottom,
        )
    }

    private fun systemBarInsets(insets: WindowInsets): List<Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            listOf(bars.left, bars.top, bars.right, bars.bottom)
        } else {
            @Suppress("DEPRECATION")
            listOf(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom,
            )
        }
}
