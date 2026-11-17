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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors
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
    private val popup = Animatable(0f)
    private var runToken by mutableIntStateOf(0)

    private val star = Path()
    private var strokeWidth = -1f
    private var stroke = Stroke(1f)

    /** Plays the effect for [token] (~550 ms). Cancelled by a newer token; returns when finished. */
    suspend fun run(token: Int) = coroutineScope {
        t.snapTo(0f)
        pop.snapTo(0f)
        popup.snapTo(0f)
        runToken = token
        launch { pop.animateTo(1f, spring(dampingRatio = POP_DAMPING, stiffness = Spring.StiffnessMedium)) }
        launch { popup.animateTo(1f, tween(POPUP_MS, easing = LinearEasing)) }
        t.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
    }

    /** Linear 0..1 "+N" popup time for [token] (longer than [time]: ~1100 ms); 0 until [run] has started for it. */
    fun popupTime(token: Int): Float = if (runToken == token) popup.value else 0f

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

        /** "+N" popup lifetime: quick fade-in, hold, float up, fade out. */
        const val POPUP_MS = 1100
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
internal fun DrawScope.drawPlacementFx(
    fx: PlacementFx,
    token: Int,
    idx: Int,
    board: BoardUi,
    gapPx: Float,
    /** Low-RAM device: draw 4 of the 8 sparkles. */
    halfSparkles: Boolean = false,
) {
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
        val corner = CornerRadius(BoardLayoutCalc.cellCornerPx(cell, 1.dp.toPx()))
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
    val sparkleStep = if (halfSparkles) 2 else 1
    for (i in 0 until SPARKLES step sparkleStep) {
        val even = (i / sparkleStep) % 2 == 0
        val dist = cell * (0.2f + 0.8f * ease2) * (if (even) 1f else 0.8f)
        val path = fx.starPath(cx + RAY_COS[i] * dist, cy + RAY_SIN[i] * dist, if (even) baseR else baseR * 0.7f)
        drawPath(path, (if (even) GOLD else tint).copy(alpha = alpha))
    }
}

/**
 * "+N" score popup, drawn LAST (above cells, region wave and sparkles) over ~[PlacementFx.POPUP_MS]: fade-in 0..8 %, hold to 60 %
 * while floating up, fade-out after. [plus] / [plusOutline] are pre-measured (cached by the caller); null = no popup.
 */
internal fun DrawScope.drawScorePopup(
    fx: PlacementFx,
    token: Int,
    idx: Int,
    board: BoardUi,
    gapPx: Float,
    plus: TextLayoutResult?,
    fill: Color,
) {
    if (plus == null) return
    val n = board.size
    if (idx !in 0 until n * n) return
    val cell = (size.width - gapPx * (n - 1)) / n
    if (cell <= 0f) return
    val p = fx.popupTime(token)
    if (p >= 1f) return
    val pitch = cell + gapPx
    val cx = (idx % n) * pitch + cell / 2f
    val cy = (idx / n) * pitch + cell / 2f
    val a = when {
        p < 0.08f -> p / 0.08f
        p < 0.6f -> 1f
        else -> 1f - (p - 0.6f) / 0.4f
    }.coerceIn(0f, 1f)
    val inv = 1f - p
    val rise = cell * 0.9f * (1f - inv * inv * inv)
    val w = plus.size.width.toFloat()
    val h = plus.size.height.toFloat()
    val x = (cx - w / 2f).coerceIn(0f, max(0f, size.width - w)) // keep inside the board's left/right edges
    // Starts on the cell's top edge (off the cat / X centre) and floats up from there.
    val topLeft = Offset(x, cy - cell * 0.5f - h / 2f - rise)
    // Stroke FIRST (white, round joins, ~13 % of font size visible), solid fill on top. Explicit colour + drawStyle override the
    // layout's style so nothing depends on how it was measured. No shadow: it only blurs the glyph edges.
    val outlineW = h * 0.2f // line height ~1.25 x font -> ~0.25 x font total; inner half is covered by the fill
    drawText(plus, color = POPUP_OUTLINE, topLeft = topLeft, alpha = a, drawStyle = Stroke(width = outlineW, join = StrokeJoin.Round))
    drawText(plus, color = fill, topLeft = topLeft, alpha = a, drawStyle = Fill)
}

private val POPUP_OUTLINE = Color.White // white halo: dark fill + white stroke reads on light pink / cream / pastel cells

// Dark saturated "+N" fills: teal, blue, purple, orange-red, magenta.
private val POPUP_FILLS = listOf(
    Color(0xFF00695C), Color(0xFF1565C0), Color(0xFF6A1B9A), Color(0xFFD84315), Color(0xFFAD1457),
)

/**
 * Random "+N" popup fill, independent of score. Call once per popup (remember keyed on the placement token). [prev] is a
 * one-slot holder of the last picked index so two consecutive popups never share a colour.
 */
internal fun pickScorePopupFill(prev: IntArray): Color {
    val i = if (prev[0] < 0) {
        kotlin.random.Random.nextInt(POPUP_FILLS.size)
    } else {
        val r = kotlin.random.Random.nextInt(POPUP_FILLS.size - 1)
        if (r >= prev[0]) r + 1 else r // skip prev
    }
    prev[0] = i
    return POPUP_FILLS[i]
}

private const val WAVE_START = 0.04f
private const val WAVE_STEP = 0.045f // per cell of distance
private const val WAVE_POP = 0.32f
private const val WAVE_SWELL = 0.3f

/**
 * Scale of an X mark at ring distance [dist] (>= 1, along the placed cat's row / column or one of its 8 neighbours) at placement
 * effect time [t] (0..1): a staggered pop radiating outward. 1 outside its window. Pure, allocation-free.
 */
internal fun xWaveScale(t: Float, dist: Int): Float {
    val local = (t - WAVE_START - dist * WAVE_STEP) / WAVE_POP
    return if (local <= 0f || local >= 1f) 1f else 1f + WAVE_SWELL * sin(PI * local).toFloat()
}
