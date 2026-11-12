package com.tm.meow.sudoku.puzzle.ui.screens.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Linear 0..1 clock for the shooting star. Token-gated like [PlacementFx]; every read happens in a draw lambda. */
@Stable
internal class ShootingStarFx {
    private val t = Animatable(0f)
    private var runToken by mutableIntStateOf(0)

    suspend fun run(token: Int) {
        t.snapTo(0f)
        runToken = token
        t.animateTo(1f, tween(StarFlight.DURATION_MS, easing = LinearEasing))
    }

    fun time(token: Int): Float = if (runToken == token) t.value else 0f
}

/** Delay before the region tab springs in: the star lands on it (a little before the end of the flight). */
internal const val STAR_ARRIVE_DELAY_MS = 480L

private const val TRAIL = 6
private val StarGold = Color(0xFFFFD54F)
private val StarCore = Color(0xFFFFF8E1)
private val STAR_COS = FloatArray(10) { cos(it * PI / 5 - PI / 2).toFloat() }
private val STAR_SIN = FloatArray(10) { sin(it * PI / 5 - PI / 2).toFloat() }

/**
 * Shooting star: on a region clear a gold star with a short fading trail flies from the placed cat's cell to that region's icon in
 * the tab strip (~[StarFlight.DURATION_MS] ms, finite). One Canvas over the whole game area, ABOVE board + strip, BELOW the dialogs
 * (the caller places it before them); it never reaches the bottom, so the native ad bar is untouched. Positions come from
 * [boardGeo] (board root bounds) and [tabCenters] (region -> icon centre in root space); both are read in the draw phase only.
 * Same token pattern as the board effects: a token already present on first composition never plays; skipped when animations are off.
 */
@Composable
internal fun ShootingStarOverlay(
    regionCleared: RegionClearedUi?,
    boardGeo: State<BoardGeo?>,
    tabCenters: SnapshotStateMap<Int, Offset>,
    modifier: Modifier = Modifier,
) {
    val animationsOn = rememberAnimationsEnabled(null)
    val fx = remember { ShootingStarFx() }
    var done by remember { mutableIntStateOf(regionCleared?.token ?: 0) }
    LaunchedEffect(regionCleared?.token) {
        val rc = regionCleared ?: return@LaunchedEffect
        if (rc.token == done) return@LaunchedEffect
        if (animationsOn && rc.row >= 0) fx.run(rc.token)
        done = rc.token
    }
    val rc = regionCleared
    val token = if (animationsOn && rc != null && rc.row >= 0 && rc.token != done) rc.token else 0
    if (token == 0 || rc == null) return
    val origin = remember { mutableStateOf(Offset.Zero) }
    val star = remember { Path() }
    val pos = remember { FloatArray(2) }
    Canvas(
        modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .onGloballyPositioned { origin.value = it.positionInRoot() },
    ) {
        val t = fx.time(token)
        if (t <= 0f || t >= 1f) return@Canvas
        val geo = boardGeo.value ?: return@Canvas
        val end = tabCenters[rc.region] ?: return@Canvas
        val metrics = geo.metrics
        val cell = metrics.cellDp.dp.toPx()
        val pitch = cell + metrics.gapDp.dp.toPx()
        val pad = metrics.cardPaddingDp.dp.toPx()
        val x0 = geo.bounds.left - origin.value.x + pad + rc.col * pitch + cell / 2f
        val y0 = geo.bounds.top - origin.value.y + pad + rc.row * pitch + cell / 2f
        val x1 = end.x - origin.value.x
        val y1 = end.y - origin.value.y
        val dx = x1 - x0
        val dy = y1 - y0
        // Control point: midpoint pushed sideways, so the flight arcs instead of running a straight line.
        val cx = (x0 + x1) / 2f - dy * 0.3f
        val cy = (y0 + y1) / 2f + dx * 0.3f
        val fade = if (t > 0.9f) (1f - t) / 0.1f else 1f
        val r = 11.dp.toPx()

        // Trail: older samples are smaller and fainter.
        for (k in TRAIL downTo 1) {
            val tk = StarFlight.ease((t - k * 0.04f).coerceAtLeast(0f))
            StarFlight.bezier(tk, x0, y0, cx, cy, x1, y1, pos)
            val f = 1f - k / (TRAIL + 1f)
            drawCircle(StarGold.copy(alpha = 0.5f * f * fade), radius = r * 0.55f * f, center = Offset(pos[0], pos[1]))
        }
        StarFlight.bezier(StarFlight.ease(t), x0, y0, cx, cy, x1, y1, pos)
        val hx = pos[0]
        val hy = pos[1]
        rotate(t * 540f, Offset(hx, hy)) {
            drawStar(star, hx, hy, r, StarGold.copy(alpha = fade))
            drawStar(star, hx, hy, r * 0.5f, StarCore.copy(alpha = fade))
        }
    }
}

private fun DrawScope.drawStar(path: Path, cx: Float, cy: Float, r: Float, color: Color) {
    path.rewind()
    for (k in 0 until 10) {
        val rad = if (k % 2 == 0) r else r * 0.45f
        val x = cx + STAR_COS[k] * rad
        val y = cy + STAR_SIN[k] * rad
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}
