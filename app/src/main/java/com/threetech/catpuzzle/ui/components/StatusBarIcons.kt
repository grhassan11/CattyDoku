package com.threetech.catpuzzle.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Sets the status bar icon colour while this composable is in composition: [lightIcons] = true for a dark backdrop
 * (light icons), false for a bright one (dark icons). Restores the previous appearance on dispose, so other screens keep
 * the app default (dark icons, set once in MainActivity). Only re-applies when [lightIcons] changes.
 */
@Composable
fun StatusBarIconsEffect(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.findWindow() ?: return
    // Declared first: runs before the SideEffect below, so [previous] is the value from before this screen changed it.
    DisposableEffect(window, view) {
        val controller = WindowInsetsControllerCompat(window, view)
        val previous = controller.isAppearanceLightStatusBars
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
    SideEffect {
        // isAppearanceLightStatusBars = true means DARK icons (for a light bar).
        WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = !lightIcons
    }
}

private fun Context.findWindow(): Window? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx.window
        ctx = ctx.baseContext
    }
    return null
}
