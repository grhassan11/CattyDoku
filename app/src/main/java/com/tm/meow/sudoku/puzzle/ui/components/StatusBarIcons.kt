package com.tm.meow.sudoku.puzzle.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Status bar icons are ALWAYS dark (black) app-wide, on every screen including dark worlds and dialogs.
 * Re-asserts dark icons while in composition (guards against any other code flipping them).
 * Global default is also set in MainActivity + themes.xml.
 */
@Composable
fun StatusBarIconsEffect() {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.findWindow() ?: return
    SideEffect {
        // isAppearanceLightStatusBars = true means DARK icons.
        WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = true
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
