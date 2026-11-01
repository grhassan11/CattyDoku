package com.threetech.catpuzzle.ui.screens.home.worlds

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/*
 * Boss-node (level 10, 20, ...) body shapes, moved out of the TEMP node gallery. Index 0..4 comes from
 * domain.model.BossNodeShapes.indexFor(level). Each shape paints a lip layer (fixed) and a face layer (drops by
 * [BossMetrics.travel] on press) from the node's [NodePalette], so done / current / locked colours come for free.
 * Order: 0 Hex gem, 1 Cushion, 2 Coin, 3 Fish tin, 4 Yarn ball (cat / paw shapes removed).
 */

/** [travel] face drop px (press), [lip] lip depth px, [faceH] face area height px. */
internal class BossMetrics(val travel: Float, val lip: Float, val faceH: Float, val pal: NodePalette)

/** Number / lock centre offset from the face centre, as a fraction of node size. */
internal fun bossNumberYFraction(index: Int): Float = when (index) {
    3 -> -0.19f
    else -> 0f
}

internal fun DrawScope.drawBossShape(index: Int, state: NodeState, m: BossMetrics) {
    when (index) {
        0 -> drawHexGem(m)
        1 -> drawCushion(m)
        2 -> drawCoin(m)
        3 -> drawFishTin(m)
        else -> drawYarn(m)
    }
}

private fun DrawScope.drawLayered(
    m: BossMetrics,
    shapes: DrawScope.(Brush) -> Unit,
    face: DrawScope.() -> Unit,
) {
    translate(top = m.lip) { this.shapes(SolidColor(m.pal.lip)) }
    translate(top = m.travel) {
        this.shapes(Brush.verticalGradient(listOf(m.pal.top, m.pal.bottom), startY = 0f, endY = m.faceH))
        this.face()
    }
}

private fun DrawScope.highlightArc(topLeft: Offset, arcSize: Size, alpha: Float = 0.75f, width: Dp = 3.dp) {
    drawArc(
        Color.White.copy(alpha = alpha), 200f, 55f, false, topLeft, arcSize,
        style = Stroke(width = width.toPx(), cap = StrokeCap.Round),
    )
}

private fun bossHexPath(c: Offset, r: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val a = Math.toRadians(-90.0 + 60.0 * i).toFloat()
        val x = c.x + r * cos(a)
        val y = c.y + r * sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.drawHexGem(m: BossMetrics) {
    val w = size.width
    val r = m.faceH / 2f
    val c = Offset(w / 2f, r)
    val hex = bossHexPath(c, r)
    drawLayered(
        m,
        shapes = { fill ->
            drawPath(hex, fill)
            drawPath(hex, fill, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
        },
        face = {
            val vs = List(6) { i ->
                val a = Math.toRadians(-90.0 + 60.0 * i).toFloat()
                Offset(c.x + r * cos(a), c.y + r * sin(a))
            }
            for (i in 0 until 6) {
                val tri = Path().apply {
                    moveTo(c.x, c.y); lineTo(vs[i].x, vs[i].y); lineTo(vs[(i + 1) % 6].x, vs[(i + 1) % 6].y); close()
                }
                val col = when (i) {
                    5, 0 -> Color.White.copy(alpha = 0.22f)
                    2, 3 -> Color.Black.copy(alpha = 0.14f)
                    else -> Color.Transparent
                }
                drawPath(tri, col)
            }
            drawPath(bossHexPath(c, r * 0.78f), Color.White.copy(alpha = 0.45f), style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))
            drawCircle(Color.White.copy(alpha = 0.85f), 2.dp.toPx(), Offset(c.x - r * 0.45f, c.y - r * 0.55f))
        },
    )
}

private fun DrawScope.drawCushion(m: BossMetrics) {
    val w = size.width
    val fh = m.faceH
    val left = w * 0.03f
    val bw = w * 0.94f
    drawLayered(
        m,
        shapes = { fill ->
            drawRoundRect(fill, Offset(left, 0f), Size(bw, fh), CornerRadius(fh * 0.3f))
        },
        face = {
            val p = 4.dp.toPx()
            val puffTL = Offset(left + p, p)
            val puffSize = Size(bw - 2f * p, fh - 2f * p)
            val puffR = CornerRadius(fh * 0.27f)
            drawRoundRect(
                Brush.verticalGradient(listOf(lerp(m.pal.top, Color.White, 0.2f), m.pal.bottom), 0f, fh),
                puffTL, puffSize, puffR,
            )
            drawRoundRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
                    center = Offset(w * 0.35f, fh * 0.28f),
                    radius = w * 0.45f,
                ),
                puffTL, puffSize, puffR,
            )
            val s = 8.dp.toPx()
            drawRoundRect(
                Color.White.copy(alpha = 0.8f),
                Offset(left + s, s), Size(bw - 2f * s, fh - 2f * s), CornerRadius(fh * 0.22f),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                ),
            )
            val b = 14.dp.toPx()
            for (cx in listOf(left + b, left + bw - b)) {
                for (cy in listOf(b, fh - b)) {
                    drawCircle(m.pal.lip, 3.dp.toPx(), Offset(cx, cy))
                    drawCircle(Color.White.copy(alpha = 0.6f), 1.dp.toPx(), Offset(cx - 1.dp.toPx(), cy - 1.dp.toPx()))
                }
            }
        },
    )
}

private fun DrawScope.drawCoin(m: BossMetrics) {
    val w = size.width
    val r = m.faceH / 2f
    val center = Offset(w / 2f, r)
    drawLayered(
        m,
        shapes = { fill -> drawCircle(fill, r, center) },
        face = {
            drawCircle(m.pal.lip.copy(alpha = 0.45f), r * 0.8f, center, style = Stroke(width = 2.dp.toPx()))
            highlightArc(
                Offset(center.x - r * 0.74f, center.y - r * 0.74f), Size(r * 1.48f, r * 1.48f),
                alpha = 0.85f, width = 4.dp,
            )
            drawCircle(Color.White.copy(alpha = 0.8f), 2.dp.toPx(), Offset(center.x - r * 0.5f, center.y - r * 0.55f))
        },
    )
}

private fun DrawScope.drawFishTin(m: BossMetrics) {
    val w = size.width
    val fh = m.faceH
    val eh = fh * 0.52f
    val bh = fh - eh
    val x0 = w * 0.06f
    val bw = w * 0.88f
    val inset = 4.dp.toPx()
    drawLayered(
        m,
        shapes = { fill ->
            drawRect(fill, Offset(x0, eh / 2f), Size(bw, bh))
            drawOval(fill, Offset(x0, bh), Size(bw, eh))
            drawOval(fill, Offset(x0, 0f), Size(bw, eh))
        },
        face = {
            val shade = Color.Black.copy(alpha = 0.18f)
            drawRect(shade, Offset(x0, eh / 2f), Size(bw, bh))
            drawOval(shade, Offset(x0, bh), Size(bw, eh))
            val cx = w * 0.46f
            val cy = fh * 0.66f
            val fishColor = Color.White.copy(alpha = 0.6f)
            drawOval(fishColor, Offset(cx - w * 0.13f, cy - fh * 0.05f), Size(w * 0.26f, fh * 0.1f))
            val tail = Path().apply {
                moveTo(cx + w * 0.1f, cy)
                lineTo(cx + w * 0.19f, cy - fh * 0.06f)
                lineTo(cx + w * 0.19f, cy + fh * 0.06f)
                close()
            }
            drawPath(tail, fishColor)
            drawOval(
                Brush.verticalGradient(listOf(lerp(m.pal.top, Color.White, 0.5f), m.pal.top), 0f, eh),
                Offset(x0, 0f), Size(bw, eh),
            )
            drawOval(
                Brush.verticalGradient(listOf(m.pal.top, m.pal.bottom), 0f, eh),
                Offset(x0 + inset, inset * 0.8f), Size(bw - 2f * inset, eh - inset * 1.4f),
            )
            highlightArc(Offset(w * 0.16f, fh * 0.05f), Size(w * 0.68f, eh * 0.78f), 0.8f, 2.5.dp)
        },
    )
}

private fun DrawScope.drawYarn(m: BossMetrics) {
    val w = size.width
    val fh = m.faceH
    val r = fh * 0.42f
    val c = Offset(w * 0.46f, fh * 0.5f)
    val ball = Path().apply { addOval(Rect(c, r)) }
    drawLayered(
        m,
        shapes = { fill -> drawCircle(fill, r, c) },
        face = {
            val strand = m.pal.lip.copy(alpha = 0.55f)
            clipPath(ball) {
                for (k in -1..1) {
                    rotate(-35f, c) {
                        drawOval(strand, Offset(c.x - r * 1.3f, c.y - r * 0.5f + k * r * 0.55f), Size(r * 2.6f, r), style = Stroke(width = 2.dp.toPx()))
                    }
                }
                for (k in -1..1) {
                    rotate(40f, c) {
                        drawOval(Color.White.copy(alpha = 0.25f), Offset(c.x - r * 1.3f, c.y - r * 0.5f + k * r * 0.6f), Size(r * 2.6f, r), style = Stroke(width = 1.5.dp.toPx()))
                    }
                }
            }
            val tail = Path().apply {
                moveTo(c.x + r * 0.55f, c.y + r * 0.8f)
                cubicTo(w * 0.75f, fh * 1.02f, w * 1.0f, fh * 0.85f, w * 0.88f, fh * 0.68f)
            }
            drawPath(tail, m.pal.lip, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            highlightArc(Offset(c.x - r * 0.8f, c.y - r * 0.8f), Size(r * 1.6f, r * 1.6f))
        },
    )
}
