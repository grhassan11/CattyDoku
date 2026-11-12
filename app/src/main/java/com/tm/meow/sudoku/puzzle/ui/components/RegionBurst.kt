package com.tm.meow.sudoku.puzzle.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Max pieces per burst (one Canvas, one pass). */
const val REGION_BURST_PIECES = 36

private class BurstPieces(origins: FloatArray, cellPx: Float, colors: List<Color>, seed: Int, val n: Int) {
    val ox = FloatArray(n)
    val oy = FloatArray(n)
    val vx = FloatArray(n)
    val vy = FloatArray(n)
    val size = FloatArray(n)
    val spin = FloatArray(n)
    val color = Array(n) { Color.White }

    init {
        val rnd = Random(seed)
        val count = origins.size / 2
        for (i in 0 until n) {
            if (count > 0) {
                val k = (i % count) * 2
                ox[i] = origins[k] + (rnd.nextFloat() - 0.5f) * cellPx * 0.6f
                oy[i] = origins[k + 1] + (rnd.nextFloat() - 0.5f) * cellPx * 0.6f
            }
            val ang = rnd.nextFloat() * 6.2831855f
            val speed = cellPx * (0.7f + rnd.nextFloat() * 1.6f)
            vx[i] = cos(ang) * speed
            vy[i] = sin(ang) * speed - cellPx * 0.9f // upward bias
            size[i] = cellPx * (0.07f + rnd.nextFloat() * 0.08f)
            spin[i] = (rnd.nextFloat() - 0.5f) * 720f
            if (colors.isNotEmpty()) color[i] = colors[rnd.nextInt(colors.size)]
        }
    }
}

/**
 * Finite confetti / spark burst over a cleared region. At most [REGION_BURST_PIECES] pieces on ONE [Canvas]; pieces are built
 * once per [token]. The caller owns the clock: [progress] (0..1, read only inside the draw lambda), so the burst never
 * recomposes while it plays and nothing runs forever. [origins] = x,y pairs (px, in this Canvas' space) of the region's cell
 * centres; [cellPx] scales sizes and travel. Draws nothing at progress <= 0 or >= 1.
 */
@Composable
fun RegionBurst(
    token: Int,
    progress: () -> Float,
    origins: FloatArray,
    cellPx: Float,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    // Low-RAM devices get half the pieces (still one Canvas, same cap logic).
    val count = scaledParticleCount(REGION_BURST_PIECES, rememberLowRamDevice())
    val pieces = remember(token, count) { BurstPieces(origins, cellPx, colors, token, count) }
    Canvas(modifier) {
        val p = progress()
        if (p <= 0f || p >= 1f) return@Canvas
        val fade = if (p < 0.55f) 1f else 1f - (p - 0.55f) / 0.45f
        val g = cellPx * 3.2f * p * p
        for (i in 0 until pieces.n) {
            val x = pieces.ox[i] + pieces.vx[i] * p
            val y = pieces.oy[i] + pieces.vy[i] * p + g
            val s = pieces.size[i]
            val c = pieces.color[i].copy(alpha = fade)
            if (i % 2 == 0) {
                drawCircle(c, radius = s, center = Offset(x, y))
            } else {
                rotate(pieces.spin[i] * p, Offset(x, y)) {
                    drawRect(c, topLeft = Offset(x - s, y - s * 0.6f), size = Size(s * 2f, s * 1.2f))
                }
            }
        }
    }
}
