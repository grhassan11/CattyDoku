package com.tm.meow.sudoku.puzzle.ui.components

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * "Reduce effects" setting (Settings.reduceEffects), provided once at the activity root from the settings flow.
 * Default false = effects on. Read by rememberAnimationsEnabled, so every gated animation reacts to it.
 */
val LocalReduceEffects = compositionLocalOf { false }

/** Particle-count helper: halves [count] on low-RAM devices (never below [min]). Pure, unit tested. */
fun scaledParticleCount(count: Int, lowRam: Boolean, min: Int = 4): Int =
    if (lowRam) (count / 2).coerceAtLeast(min.coerceAtMost(count)) else count

/** True on [ActivityManager.isLowRamDevice] devices. Read once per composition host. */
@Composable
fun rememberLowRamDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        }.getOrDefault(false)
    }
}
