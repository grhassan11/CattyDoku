package com.tm.meow.sudoku.puzzle.ui.screens.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Region-clear clock for the board (tint wave + confetti burst share it). One linear 0..1 [Animatable] over [DURATION_MS];
 * all reads happen in draw lambdas. [runToken] gates reads so a stale finished value never flashes before [run] starts.
 */
@Stable
internal class RegionClearFx {
    private val t = Animatable(0f)
    private var runToken by mutableIntStateOf(0)

    suspend fun run(token: Int) {
        t.snapTo(0f)
        runToken = token
        t.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
    }

    /** Linear 0..1 effect time for [token]; 0 until [run] has started for it. */
    fun time(token: Int): Float = if (runToken == token) t.value else 0f

    companion object {
        const val DURATION_MS = 900
    }
}

private const val WAVE_STEP = 0.07f // delay per cell of distance, fraction of the clock
private const val WAVE_LEN = 0.28f // one cell's pulse length, fraction of the clock
private const val WAVE_ALPHA = 0.5f

/**
 * Tint ripple across every cell of the cleared region, radiating from the placed cat (Chebyshev distance). Drawn in the board
 * overlay after the cells. Draw-phase only. [t] is exclusive 0..1.
 */
internal fun DrawScope.drawRegionWave(t: Float, rc: RegionClearedUi, board: BoardUi, gapPx: Float) {
    val n = board.size
    val cell = (size.width - gapPx * (n - 1)) / n
    if (cell <= 0f || t <= 0f || t >= 1f) return
    val pitch = cell + gapPx
    val tint = lerp(GameColors.region(rc.region), Color.White, 0.45f)
    val corner = CornerRadius(BoardLayoutCalc.cellCornerPx(cell, 1.dp.toPx()))
    val sq = Size(cell, cell)
    val cells = board.cells
    for (i in cells.indices) {
        val c = cells[i]
        if (c.region != rc.region) continue
        val d = if (rc.row >= 0) max(abs(c.row - rc.row), abs(c.col - rc.col)) else 0
        val local = (t - d * WAVE_STEP) / WAVE_LEN
        if (local <= 0f || local >= 1f) continue
        val a = WAVE_ALPHA * sin(PI * local).toFloat()
        drawRoundRect(tint.copy(alpha = a), topLeft = Offset(c.col * pitch, c.row * pitch), size = sq, cornerRadius = corner)
    }
}

/**
 * Region tab feedback: the cleared region's icon springs in and its colour dot flashes. Two Animatables, read in graphicsLayer /
 * drawBehind only. [runToken] gating as above.
 */
@Stable
internal class TabClearFx {
    private val pop = Animatable(0f)
    private val flash = Animatable(0f)
    private var runToken by mutableIntStateOf(0)

    suspend fun run(token: Int) = coroutineScope {
        pop.snapTo(0f)
        flash.snapTo(0f)
        runToken = token
        launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
        flash.animateTo(1f, tween(FLASH_MS, easing = LinearEasing))
    }

    /** Icon scale for [token]: 0.4 -> overshoot -> 1. Before [run] reaches this token it reads [FROM] (no full-size flash first frame). */
    fun scale(token: Int): Float = if (runToken == token) FROM + (1f - FROM) * pop.value else FROM

    /** Dot flash progress 0..1 for [token]; 1 (finished) when idle / not this token. */
    fun flash(token: Int): Float = if (runToken == token) flash.value else 1f

    companion object {
        const val FLASH_MS = 600
        const val FROM = 0.4f
    }
}

/** Icon spring-in for the cleared tab ([active] only for that region). Read in the layer phase. */
internal fun Modifier.clearIconPop(fx: TabClearFx, token: Int, active: Boolean): Modifier =
    if (!active || token == 0) this else graphicsLayer {
        val s = fx.scale(token)
        scaleX = s
        scaleY = s
    }

/** Colour-dot flash: dot swells and a white halo expands + fades. Read in the layer / draw phase. */
internal fun Modifier.clearDotFlash(fx: TabClearFx, token: Int, active: Boolean): Modifier =
    if (!active || token == 0) this else graphicsLayer {
        val f = fx.flash(token)
        val s = if (f >= 1f) 1f else 1f + 0.9f * sin(PI * f).toFloat()
        scaleX = s
        scaleY = s
    }.drawBehind {
        val f = fx.flash(token)
        if (f < 1f) {
            drawCircle(
                Color.White.copy(alpha = 0.85f * (1f - f)),
                radius = size.minDimension / 2f * (1f + 1.6f * f),
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
    }
