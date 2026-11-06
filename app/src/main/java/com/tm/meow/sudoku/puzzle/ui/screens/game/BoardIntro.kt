package com.tm.meow.sudoku.puzzle.ui.screens.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Shared new-board intro wave (Levels + Sudoku): a corner-diagonal sweep, each cell scales 0.6 -> 1 (cubic ease-out) while
 * fading in. One linear [progress] clock (0..[totalMs]) is read ONLY inside graphicsLayer lambdas, so animating it never
 * recomposes. Cell (r, c) starts at `staggerMs * (r + c) / (2 * (n - 1))` and runs [cellMs]; [extraDelayMs] pushes an element
 * (e.g. a digit) later along the same wave. Total length is fixed ([totalMs]) for every grid size.
 */
internal class BoardIntro(
    private val progress: Animatable<Float, AnimationVector1D>,
    val running: Boolean,
    private val cellMs: Float,
    private val staggerMs: Float,
) {
    /** Layer modifier for cell ([row], [col]) of an [n] x [n] grid; [Modifier] (no-op) when the intro is not running. */
    fun layer(row: Int, col: Int, n: Int, extraDelayMs: Float = 0f): Modifier {
        if (!running) return Modifier
        val maxStep = (2 * (n - 1)).coerceAtLeast(1)
        val delayMs = staggerMs * (row + col) / maxStep + extraDelayMs
        return Modifier.graphicsLayer {
            val t = ((progress.value - delayMs) / cellMs).coerceIn(0f, 1f)
            val inv = 1f - t
            val e = 1f - inv * inv * inv
            val sc = 0.6f + 0.4f * e
            scaleX = sc
            scaleY = sc
            alpha = t
        }
    }
}

/**
 * Creates the intro clock. Plays once per non-null [introKey] (saveable per key: rotation / resume / undo never replay it; a
 * changed key does). Never runs when [animationsOn] is false or [introKey] is null.
 * [totalMs] = stagger + [cellMs] + [tailMs] (tail = extra room for late elements such as digits).
 */
@Composable
internal fun rememberBoardIntro(
    introKey: Long?,
    animationsOn: Boolean,
    totalMs: Float = 1500f,
    cellMs: Float = 600f,
    tailMs: Float = 0f,
): BoardIntro {
    var done by rememberSaveable(introKey) { mutableStateOf(false) }
    val running = introKey != null && animationsOn && !done
    val progress = remember(introKey) { Animatable(0f) }
    LaunchedEffect(introKey, running) {
        if (!running) return@LaunchedEffect
        progress.animateTo(totalMs, tween(totalMs.toInt(), easing = LinearEasing))
        done = true
    }
    return BoardIntro(progress, running, cellMs, totalMs - cellMs - tailMs)
}
