package com.threetech.catpuzzle.ui.screens.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect

/** Number of distinct Pattern Mode patterns. Region id maps to pattern id = id mod this. */
const val PATTERN_COUNT = 10

fun patternIdForRegion(region: Int): Int = region.mod(PATTERN_COUNT)

/**
 * Pattern Mode overlay for one cell (color-blind aid). Simple strokes/fills over the region color,
 * clipped to the cell square at [topLeft] with side [s].
 * ids: 0 dots, 1 slash, 2 h-stripes, 3 v-stripes, 4 crosshatch, 5 checker, 6 backslash, 7 rings,
 * 8 triangles, 9 zigzag.
 */
fun DrawScope.drawRegionPattern(patternId: Int, topLeft: Offset, s: Float, color: Color) {
    val w = (s * 0.055f).coerceAtLeast(1.2f)
    val step = s / 4f
    clipRect(topLeft.x, topLeft.y, topLeft.x + s, topLeft.y + s) {
        when (patternIdForRegion(patternId)) {
            0 -> for (i in 0 until 3) for (j in 0 until 3) {
                drawCircle(color, s * 0.07f, Offset(topLeft.x + s * (0.2f + 0.3f * i), topLeft.y + s * (0.2f + 0.3f * j)))
            }
            1 -> diagonals(topLeft, s, step, w, color, slash = true)
            2 -> for (i in 1 until 4) {
                val y = topLeft.y + step * i
                drawLine(color, Offset(topLeft.x, y), Offset(topLeft.x + s, y), w)
            }
            3 -> for (i in 1 until 4) {
                val x = topLeft.x + step * i
                drawLine(color, Offset(x, topLeft.y), Offset(x, topLeft.y + s), w)
            }
            4 -> {
                for (i in 1 until 4) {
                    drawLine(color, Offset(topLeft.x, topLeft.y + step * i), Offset(topLeft.x + s, topLeft.y + step * i), w)
                    drawLine(color, Offset(topLeft.x + step * i, topLeft.y), Offset(topLeft.x + step * i, topLeft.y + s), w)
                }
            }
            5 -> for (i in 0 until 4) for (j in 0 until 4) {
                if ((i + j) % 2 == 0) drawRect(color, Offset(topLeft.x + step * i, topLeft.y + step * j), Size(step, step))
            }
            6 -> diagonals(topLeft, s, step, w, color, slash = false)
            7 -> for (i in 0 until 2) for (j in 0 until 2) {
                drawCircle(
                    color,
                    s * 0.16f,
                    Offset(topLeft.x + s * (0.27f + 0.46f * i), topLeft.y + s * (0.27f + 0.46f * j)),
                    style = Stroke(w),
                )
            }
            8 -> for (i in 0 until 2) for (j in 0 until 2) {
                val cx = topLeft.x + s * (0.27f + 0.46f * i)
                val cy = topLeft.y + s * (0.27f + 0.46f * j)
                val r = s * 0.15f
                val tri = Path().apply {
                    moveTo(cx, cy - r)
                    lineTo(cx + r, cy + r)
                    lineTo(cx - r, cy + r)
                    close()
                }
                drawPath(tri, color)
            }
            else -> for (row in 1 until 4) {
                val y = topLeft.y + step * row
                val z = Path().apply {
                    moveTo(topLeft.x, y)
                    var x = topLeft.x
                    var up = true
                    while (x < topLeft.x + s) {
                        x += step / 2f
                        lineTo(x, if (up) y - step * 0.3f else y + step * 0.3f)
                        up = !up
                    }
                }
                drawPath(z, color, style = Stroke(w))
            }
        }
        Unit
    }
}

private fun DrawScope.diagonals(topLeft: Offset, s: Float, step: Float, w: Float, color: Color, slash: Boolean) {
    var d = -s
    while (d <= s) {
        if (slash) {
            drawLine(color, Offset(topLeft.x + d, topLeft.y + s), Offset(topLeft.x + d + s, topLeft.y), w)
        } else {
            drawLine(color, Offset(topLeft.x + d, topLeft.y), Offset(topLeft.x + d + s, topLeft.y + s), w)
        }
        d += step
    }
}
