package com.tm.meow.sudoku.puzzle.ui.screens.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import com.tm.meow.sudoku.puzzle.ui.components.drawXMark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Wrong-move feedback clock (pure UI, no game state). One linear 0..1 [Animatable] over [DURATION_MS]; every read
 * happens in a cell's draw lambda, so a running effect never recomposes the board. [runToken] gates reads so a stale
 * finished value never flashes before [run] starts.
 */
@Stable
internal class WrongMoveFx {
    private val t = Animatable(0f)
    private var runToken by mutableIntStateOf(0)

    /** Plays the effect for [token]. Cancelled by a newer token; returns when finished. */
    suspend fun run(token: Int) {
        t.snapTo(0f)
        runToken = token
        t.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
    }

    /** Linear 0..1 effect time for [token]; 0 until [run] has started for it. */
    fun time(token: Int): Float = if (runToken == token) t.value else 0f

    companion object {
        const val DURATION_MS = 1000
    }
}

private const val TINT_MAX = 0.40f
private const val RISE_END = 0.08f // ~80 ms red flash-in
private const val HOLD_END = 0.35f // red held until 350 ms, then ease-out fade to 1000 ms
private const val HEART_POP_END = 0.22f
private const val HEART_FADE_START = 0.55f
private const val X_SHAKE_END = 0.45f
private val WrongTint = Color(0xFFE14B4B) // same red as GameColors.Conflict (outline keeps drawing on top of this)
internal val WrongX = Color(0xFFD50000)
private const val PARTNER_PULSE_END = 0.7f
private val HeartFilter = ColorFilter.tint(Color.White)

/** Horizontal shake offset (px) of a touching partner cat at effect time [t] (0..1), cell side [s]. Same damped shake as the wrong cross. */
internal fun wrongPartnerShake(t: Float, s: Float): Float {
    val q = (t / X_SHAKE_END).coerceIn(0f, 1f)
    return s * 0.07f * (1f - q) * sin(2.0 * PI * 3.0 * q).toFloat()
}

/**
 * Wrong-move overlay for ONE cell, drawn inside the cell's draw scope at effect time [t] (0..1, exclusive), cell side [s].
 * Red tint with ease-out fade, broken heart pop-in with bounce, and a red cross that shakes
 * + pops. Drawn BEFORE the conflict / hint outlines so those stay on top untouched. No allocations per frame.
 */
internal fun DrawScope.drawWrongMove(t: Float, heart: Painter?, s: Float, radius: Float, partner: Boolean = false) {
    // Red tint: fast rise, hold, ease-out fade back to the original colour. A touching partner cat pulses twice instead.
    val tint = if (partner) {
        val q = (t / PARTNER_PULSE_END).coerceIn(0f, 1f)
        if (q >= 1f) 0f else 0.5f * (1f - cos(2.0 * PI * 2.0 * q).toFloat())
    } else {
        when {
            t < RISE_END -> t / RISE_END
            t < HOLD_END -> 1f
            else -> {
                val inv = 1f - (t - HOLD_END) / (1f - HOLD_END)
                inv * inv
            }
        }
    }
    drawRoundRect(WrongTint.copy(alpha = TINT_MAX * tint), size = Size(s, s), cornerRadius = CornerRadius(radius))

    // Wrong CAT: the cell is already a normal X in state; the board skips its own X while this runs. Draw it here:
    // red, damped shake + scale pop, then it just stays red (CellUi.wrongX keeps it red after the fx: no handoff glitch).
    // (A partner cell keeps its cat: the board shakes the cat itself, see wrongPartnerShake.)
    if (!partner) run {
        val q = (t / X_SHAKE_END).coerceIn(0f, 1f)
        val damp = 1f - q
        val dx = s * 0.07f * damp * sin(2.0 * PI * 3.0 * q).toFloat()
        val sc = 1f + 0.22f * damp * sin(2.0 * PI * 1.5 * q).toFloat()
        val col = WrongX // stays red: BoardCell keeps drawing it red (CellUi.wrongX) after the fx
        translate(left = dx) {
            scale(sc, pivot = Offset(s / 2f, s / 2f)) { drawXMark(Offset.Zero, s, col) }
        }
    }

    // Broken heart: easeOutBack pop-in (0 -> ~1.1 -> 1), holds, then fades with the tint.
    if (heart != null) {
        val p = (t / HEART_POP_END).coerceIn(0f, 1f)
        val k = p - 1f
        val sc = 1f + 2.70158f * k * k * k + 1.70158f * k * k
        val a = if (t < HEART_FADE_START) {
            1f
        } else {
            val x = (t - HEART_FADE_START) / (1f - HEART_FADE_START)
            1f - x * x
        }
        val h = s * (if (partner) 0.4f else 0.5f) * sc
        if (h > 0f && a > 0f) {
            translate(left = (s - h) / 2f, top = (s - h) / 2f) {
                with(heart) { draw(Size(h, h), alpha = a, colorFilter = HeartFilter) }
            }
        }
    }
}
