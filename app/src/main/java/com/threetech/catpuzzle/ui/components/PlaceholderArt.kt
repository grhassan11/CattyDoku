package com.threetech.catpuzzle.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * PLACEHOLDER ART. Own Compose-drawn geometry for things the user icon set has no icon for.
 * Cats / fish / bulb / back / settings / close / speaker / vibrate now come from IconArt.kt
 * (user icons). What stays here, to be replaced when the user supplies icons:
 *   - drawXMark            -> exclude mark (board + rules diagram)
 *   - MusicNotePlaceholder -> music toggle tile icon
 *   - MicPlaceholder       -> voice toggle tile icon
 *   - HomePlaceholder      -> lose-dialog HOME button icon (no home icon in the user set)
 */

/** Exclude mark inside a square of side [box] at [topLeft]. */
fun DrawScope.drawXMark(topLeft: Offset, box: Float, color: Color) {
    val inset = box * 0.3f
    val w = box * 0.11f
    val a = Offset(topLeft.x + inset, topLeft.y + inset)
    val b = Offset(topLeft.x + box - inset, topLeft.y + box - inset)
    val c = Offset(topLeft.x + box - inset, topLeft.y + inset)
    val d = Offset(topLeft.x + inset, topLeft.y + box - inset)
    // soft dark outline keeps white X visible on light region colors
    val outline = Color(0xFF3A2328).copy(alpha = 0.35f * color.alpha)
    val ow = w * 1.6f
    drawLine(outline, a, b, ow, StrokeCap.Round)
    drawLine(outline, c, d, ow, StrokeCap.Round)
    drawLine(color, a, b, w, StrokeCap.Round)
    drawLine(color, c, d, w, StrokeCap.Round)
}

/** Eighth-note pair. */
@Composable
fun MusicNotePlaceholder(color: Color, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val head = s * 0.14f
        val stem = s * 0.07f
        val lx = s * 0.32f
        val rx = s * 0.74f
        // note heads
        drawOval(color, Offset(lx - head * 1.3f, s * 0.68f - head), Size(head * 2.6f, head * 2f))
        drawOval(color, Offset(rx - head * 1.3f, s * 0.58f - head), Size(head * 2.6f, head * 2f))
        // stems
        drawLine(color, Offset(lx + head * 1.15f, s * 0.68f), Offset(lx + head * 1.15f, s * 0.2f), stem, StrokeCap.Round)
        drawLine(color, Offset(rx + head * 1.15f, s * 0.58f), Offset(rx + head * 1.15f, s * 0.1f), stem, StrokeCap.Round)
        // beam
        val beam = Path().apply {
            moveTo(lx + head * 1.15f, s * 0.2f)
            lineTo(rx + head * 1.15f, s * 0.1f)
            lineTo(rx + head * 1.15f, s * 0.26f)
            lineTo(lx + head * 1.15f, s * 0.36f)
            close()
        }
        drawPath(beam, color)
    }
}

/** Microphone. */
@Composable
fun MicPlaceholder(color: Color, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(
            color,
            topLeft = Offset(s * 0.36f, s * 0.08f),
            size = Size(s * 0.28f, s * 0.5f),
            cornerRadius = CornerRadius(s * 0.14f),
        )
        val w = s * 0.07f
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(s * 0.26f, s * 0.26f),
            size = Size(s * 0.48f, s * 0.4f),
            style = Stroke(w, cap = StrokeCap.Round),
        )
        drawLine(color, Offset(s * 0.5f, s * 0.66f), Offset(s * 0.5f, s * 0.86f), w, StrokeCap.Round)
        drawLine(color, Offset(s * 0.36f, s * 0.88f), Offset(s * 0.64f, s * 0.88f), w, StrokeCap.Round)
    }
}

/** House: roof triangle + body + door cutout. Used on the lose dialog HOME button. */
@Composable
fun HomePlaceholder(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val roof = Path().apply {
            moveTo(s * 0.5f, s * 0.08f)
            lineTo(s * 0.96f, s * 0.5f)
            lineTo(s * 0.04f, s * 0.5f)
            close()
        }
        drawPath(roof, color)
        val body = Path().apply {
            moveTo(s * 0.16f, s * 0.46f)
            lineTo(s * 0.84f, s * 0.46f)
            lineTo(s * 0.84f, s * 0.9f)
            lineTo(s * 0.62f, s * 0.9f)
            lineTo(s * 0.62f, s * 0.62f)
            lineTo(s * 0.38f, s * 0.62f)
            lineTo(s * 0.38f, s * 0.9f)
            lineTo(s * 0.16f, s * 0.9f)
            close()
        }
        drawPath(body, color)
    }
}
