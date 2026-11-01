package com.threetech.catpuzzle.ui.screens.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.threetech.catpuzzle.ui.theme.GameColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * One-shot "correct cat placed" effect state for the board: pop + squish (spring), sparkle burst, glow ripple and
 * region flash. Pure UI. Two Animatables drive everything; every read happens in the draw phase, so a running effect
 * never recomposes the board. [runToken] gates reads so a stale finished value never flashes before [run] starts.
 */
@Stable
internal class PlacementFx {
    private val t = Animatable(0f)
    private val pop = Animatable(0f)
    private var runToken by mutableIntStateOf(0)

    private val star = Path()
    private var strokeWidth = -1f
    private var stroke = Stroke(1f)

    /** Plays the effect for [token] (~550 ms). Cancelled by a newer token; returns when finished. */
    suspend fun run(token: Int) = coroutineScope {
        t.snapTo(0f)
        pop.snapTo(0f)
        runToken = token
        launch { pop.animateTo(1f, spring(dampingRatio = POP_DAMPING, stiffness = Spring.StiffnessMedium)) }
        t.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
    }

    /** Linear 0..1 effect time for [token]; 0 until [run] has started for it. */
    fun time(token: Int): Float = if (runToken == token) t.value else 0f

    /** Cat scale for [token]: 0.6 -> ~1.12 overshoot -> 1.0 (spring). */
    fun popScale(token: Int): Float = POP_FROM + (1f - POP_FROM) * (if (runToken == token) pop.value else 0f)

    /** Cached stroke so the ring does not allocate a [Stroke] per frame. */
    fun ringStroke(widthPx: Float): Stroke {
        if (widthPx != strokeWidth) {
            strokeWidth = widthPx
            stroke = Stroke(widthPx)
        }
        return stroke
    }

    /** Reusable 4-point sparkle path centred on ([cx], [cy]) with outer radius [r]. */
    fun starPath(cx: Float, cy: Float, r: Float): Path {
        star.rewind()
        for (k in 0 until 8) {
            val rad = if (k % 2 == 0) r else r * STAR_INNER
            val x = cx + STAR_COS[k] * rad
            val y = cy + STAR_SIN[k] * rad
            if (k == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        return star
    }

    companion object {
        const val DURATION_MS = 550
        const val POP_FROM = 0.6f
        const val POP_DAMPING = 0.33f

        /** Horizontal / vertical squash coupled to the pop overshoot (stretch tall when big, wide when small). */
        const val SQUASH = 0.4f
    }
}

private const val FLASH_ALPHA = 0.35f
private const val FLASH_END = 0.64f // 350 ms of 550 ms
private const val RING_ALPHA = 0.6f
private const val SPARKLES = 8
private const val STAR_INNER = 0.32f
private val GOLD = Color(0xFFFFD54F)
private val RING = Color(0xFFFFE082)

/** Sparkle directions: 8 rays, offset 22.5 deg so none points straight along the grid axes. */
private val RAY_COS = FloatArray(SPARKLES) { cos(Math.toRadians(it * 45.0 + 22.5)).toFloat() }
private val RAY_SIN = FloatArray(SPARKLES) { sin(Math.toRadians(it * 45.0 + 22.5)).toFloat() }
private val STAR_COS = FloatArray(8) { cos(it * PI / 4 - PI / 2).toFloat() }
private val STAR_SIN = FloatArray(8) { sin(it * PI / 4 - PI / 2).toFloat() }

/**
 * Overlay painted over the whole cell grid, after the cells, so nothing is clipped by neighbours.
 * Region flash + glow ring + sparkle burst for the cell [idx]. [gapPx] = gap between cells.
 * Sizes derive from the cell size, so 9x9 (small cells) scales down with a readable floor.
 */
internal fun DrawScope.drawPlacementFx(fx: PlacementFx, token: Int, idx: Int, board: BoardUi, gapPx: Float) {
    val n = board.size
    if (idx !in 0 until n * n) return
    val cell = (size.width - gapPx * (n - 1)) / n
    if (cell <= 0f) return
    val t = fx.time(token)
    if (t >= 1f) return
    val pitch = cell + gapPx
    val cx = (idx % n) * pitch + cell / 2f
    val cy = (idx / n) * pitch + cell / 2f
    val region = board.cells[idx].region

    // Region flash: every cell of the placed cat's region brightens, white 0.35 -> 0 over ~350 ms.
    val flash = FLASH_ALPHA * (1f - t / FLASH_END)
    if (flash > 0f) {
        val c = Color.White.copy(alpha = flash)
        val corner = CornerRadius(8.dp.toPx())
        val sq = Size(cell, cell)
        val cells = board.cells
        for (i in cells.indices) {
            if (cells[i].region == region) {
                drawRoundRect(c, topLeft = Offset((i % n) * pitch, (i / n) * pitch), size = sq, cornerRadius = corner)
            }
        }
    }

    // Glow ripple: soft ring expanding from the cell centre, fading out.
    val inv = 1f - t
    val ease = 1f - inv * inv * inv
    drawCircle(
        RING.copy(alpha = RING_ALPHA * inv),
        radius = cell * (0.25f + 0.85f * ease),
        center = Offset(cx, cy),
        style = fx.ringStroke(max(cell * 0.06f, 1.5.dp.toPx())),
    )

    // Sparkle burst: 8 four-point stars radiating out, alternating region tint / gold, fading in the back half.
    val ease2 = 1f - inv * inv
    val alpha = if (t < 0.35f) 1f else inv / 0.65f
    val baseR = max(cell * 0.15f, 3.5.dp.toPx()) * (1f - 0.4f * t)
    val tint = lerp(GameColors.region(region), Color.White, 0.6f)
    for (i in 0 until SPARKLES) {
        val even = i % 2 == 0
        val dist = cell * (0.2f + 0.8f * ease2) * (if (even) 1f else 0.8f)
        val path = fx.starPath(cx + RAY_COS[i] * dist, cy + RAY_SIN[i] * dist, if (even) baseR else baseR * 0.7f)
        drawPath(path, (if (even) GOLD else tint).copy(alpha = alpha))
    }
}
