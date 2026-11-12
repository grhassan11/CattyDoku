package com.tm.meow.sudoku.puzzle.ui.screens.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lives-row heart-loss effect clock: a drawn pink cat paw swipes across the lost fish, the fish then swaps to the bone and a small
 * crumb burst pops. One linear 0..1 [Animatable] over [DURATION_MS]; read in a draw lambda only. No new asset (Canvas shapes).
 */
@Stable
internal class PawSwipeFx {
    val t = Animatable(0f)

    suspend fun run() {
        t.snapTo(0f)
        t.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
    }

    companion object {
        const val DURATION_MS = 700

        /** Fraction of the clock at which the paw has crossed the fish: fish -> bone swap + crumbs. */
        const val SWAP_AT = 0.5f
    }
}

private val PawPink = Color(0xFFFF9EBB)
private val PawBean = Color(0xFFF06292)
private val ClawWhite = Color(0xFFFFFFFF)
private val CrumbA = Color(0xFFF5F0E6)
private val CrumbB = Color(0xFFE2CFA8)
private const val CRUMBS = 7
private val CRUMB_COS = FloatArray(CRUMBS) { cos(it * 2.0 * PI / CRUMBS + 0.4).toFloat() }
private val CRUMB_SIN = FloatArray(CRUMBS) { sin(it * 2.0 * PI / CRUMBS + 0.4).toFloat() }

/**
 * Paw swipe + crumbs over one fish slot of side [s] (top-left = scope origin; the paw may overflow the slot). [t] 0..1 exclusive.
 * Paw travels from the upper right across the fish to the lower left, with three claw scratches trailing, then fades; crumbs burst
 * from the fish centre from [PawSwipeFx.SWAP_AT] on. Allocation-free.
 */
internal fun DrawScope.drawPawSwipe(t: Float, s: Float) {
    if (t <= 0f || t >= 1f) return
    val c = Offset(s / 2f, s / 2f)
    val sx = s * 1.35f
    val sy = -s * 0.55f
    val ex = -s * 0.35f
    val ey = s * 1.0f
    val swipe = (t / PawSwipeFx.SWAP_AT).coerceIn(0f, 1f)
    val e = swipe * swipe * (3f - 2f * swipe)
    val px = sx + (ex - sx) * e
    val py = sy + (ey - sy) * e
    val ang = Math.toDegrees(atan2((ey - sy).toDouble(), (ex - sx).toDouble())).toFloat()
    val paw = if (t < PawSwipeFx.SWAP_AT) 1f else (1f - (t - PawSwipeFx.SWAP_AT) / 0.2f).coerceIn(0f, 1f)

    // Claw scratches across the fish, drawn progressively along the swipe, fading out.
    val scratch = if (t < 0.7f) 1f else (1f - (t - 0.7f) / 0.3f).coerceIn(0f, 1f)
    if (swipe > 0.2f && scratch > 0f) {
        val reach = ((swipe - 0.2f) / 0.8f).coerceIn(0f, 1f)
        for (k in -1..1) {
            val ox = k * s * 0.16f
            val oy = -k * s * 0.16f
            val x0 = c.x + s * 0.45f + ox
            val y0 = c.y - s * 0.5f + oy
            val x1 = x0 + (-s * 0.9f) * reach
            val y1 = y0 + (s * 1.0f) * reach
            drawLine(ClawWhite.copy(alpha = 0.9f * scratch), Offset(x0, y0), Offset(x1, y1), s * 0.05f, StrokeCap.Round)
        }
    }

    if (paw > 0f) {
        rotate(ang, Offset(px, py)) {
            val pa = PawPink.copy(alpha = paw)
            val bean = PawBean.copy(alpha = paw)
            // Palm pad + four toes (toes point along +x = travel direction after the rotation).
            drawCircle(pa, radius = s * 0.30f, center = Offset(px, py))
            drawCircle(bean, radius = s * 0.13f, center = Offset(px - s * 0.02f, py))
            val tx = px + s * 0.36f
            drawCircle(pa, radius = s * 0.11f, center = Offset(tx - s * 0.04f, py - s * 0.28f))
            drawCircle(pa, radius = s * 0.11f, center = Offset(tx + s * 0.06f, py - s * 0.10f))
            drawCircle(pa, radius = s * 0.11f, center = Offset(tx + s * 0.06f, py + s * 0.10f))
            drawCircle(pa, radius = s * 0.11f, center = Offset(tx - s * 0.04f, py + s * 0.28f))
        }
    }

    // Crumb burst from the swapped fish.
    if (t > PawSwipeFx.SWAP_AT) {
        val q = ((t - PawSwipeFx.SWAP_AT) / (1f - PawSwipeFx.SWAP_AT)).coerceIn(0f, 1f)
        val out = 1f - (1f - q) * (1f - q)
        val a = 1f - q
        for (i in 0 until CRUMBS) {
            val d = s * 0.62f * out
            val x = c.x + CRUMB_COS[i] * d
            val y = c.y + CRUMB_SIN[i] * d + s * 0.35f * q * q
            drawCircle((if (i % 2 == 0) CrumbA else CrumbB).copy(alpha = a), radius = s * 0.065f * (1f - 0.5f * q), center = Offset(x, y))
        }
    }
}
