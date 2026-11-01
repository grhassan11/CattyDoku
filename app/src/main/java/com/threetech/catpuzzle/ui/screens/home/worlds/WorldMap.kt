package com.threetech.catpuzzle.ui.screens.home.worlds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.threetech.catpuzzle.domain.model.WorldRules
import com.threetech.catpuzzle.ui.components.StatusBarIconsEffect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Distance between node centres = width of one lazy item. 4 items = one wave period = 20 dash cycles (seamless wrap). */
private val ItemW = 114.dp
private val TrailWidth = 22.dp // road edge (outline)
private val CoreWidth = 14.dp // road body

/** Lane is taller than the trail band by this much above and below, so glow rings / stars are never clipped. */
private val Slack = 20.dp

/** Gap kept between the trail band and the controls above / below it. */
private val EdgeGap = 4.dp

/** Room a node needs above / below its centre (PLAY tag above; stars tray below). */
private const val NodeAbove = 104f
private const val NodeBelow = 78f

private const val AdvanceDelayMs = 350L

/** The current node counts as off screen once its centre is more than this many nodes from the viewport centre. */
private const val JumpShowDistanceNodes = 1.6f

/** Lazy item index the map centres on for [current] (opening position, post-win glide and the jump pill all use it). */
private fun focusIndex(current: Int, levels: Int): Int = (current - 1).coerceIn(0, levels)

/** Vertical wave of the trail inside the band: amplitude and the centre line (both Dp, band-relative). */
@Immutable
private class TrailShape(val amp: Dp, val midY: Dp)

/**
 * Fit the sine wave into a band of [height]: the amplitude shrinks on short bands (down to a flat line); the trail is
 * centred vertically in the band.
 */
private fun trailShape(height: Dp): TrailShape {
    val h = height.value
    val amp = ((h - (NodeAbove + NodeBelow)) / 2f).coerceIn(0f, 72f)
    val above = amp + NodeAbove
    val need = above + amp + NodeBelow
    return TrailShape(amp = amp.dp, midY = (above + (h - need) / 2f).dp)
}

/** How far the node journey is shifted up on screen (dp), limited by the headroom [trailShape] leaves above the crest. */
private const val JourneyLiftDp = 48f

private fun journeyLift(height: Dp): Dp {
    val h = height.value
    val amp = ((h - (NodeAbove + NodeBelow)) / 2f).coerceIn(0f, 72f)
    val spare = (h - (amp * 2 + NodeAbove + NodeBelow)) / 2f
    return minOf(JourneyLiftDp, spare.coerceAtLeast(0f)).dp
}

/** +1 = crest (node above the centre line), -1 = trough, 0 = on the line. Period 4 nodes; exact for any index. */
private fun waveSign(index: Int): Int = when (index and 3) {
    1 -> 1
    3 -> -1
    else -> 0
}

/**
 * Backdrop blend between world (m-1) and world m for a viewport centred on fractional node [p]: the blend runs over two
 * node-widths, centred on the boundary between the last node of a world and the first of the next (p = 10m - 1.5 ... ).
 */
private fun crossfadeT(p: Float, m: Int): Float = ((p + 1.5f - WorldRules.LEVELS_PER_WORLD * m) / 2f).coerceIn(0f, 1f)

/**
 * Home map: ONE continuous horizontal journey. A full-bleed backdrop fixed to the screen (crossfades between world
 * themes and runs parallax layers with the scroll), a wavy trail and the level nodes in a lazy row. The trail band sits
 * at [bandTop] / [bandHeight] (map-relative, between the controls); the backdrop itself runs edge to edge.
 * Stateless apart from view scroll state: progress in, [onNodeClick] out (done / current node tapped = open its detail sheet).
 */
@Composable
fun WorldMap(
    map: WorldMapUi,
    bandTop: Dp,
    bandHeight: Dp,
    onNodeClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onWorldChange: (Int) -> Unit = {},
    jumpPill: @Composable BoxScope.(direction: () -> Int, onJump: () -> Unit) -> Unit = { _, _ -> },
) {
    Box(modifier) {
        val height = bandHeight - EdgeGap * 2
        if (map.loaded && height > 0.dp) {
            LoadedWorldMap(
                map = map,
                bandTop = bandTop + EdgeGap,
                bandHeight = height,
                onNodeClick = onNodeClick,
                onWorldChange = onWorldChange,
                jumpPill = jumpPill,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LoadedWorldMap(
    map: WorldMapUi,
    bandTop: Dp,
    bandHeight: Dp,
    onNodeClick: (Int) -> Unit,
    onWorldChange: (Int) -> Unit,
    jumpPill: @Composable BoxScope.(direction: () -> Int, onJump: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val itemPx = with(density) { ItemW.toPx() }
        val sidePad = (maxWidth - ItemW) / 2 // first / last node can sit exactly in the centre
        val current = map.currentLevel
        val levels = map.visibleLevels
        val nodeCount = levels + 1 // + the "more worlds coming" tail

        // Opens on the current node, centred (item start == padding edge == centre of the viewport).
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = focusIndex(current, levels))
        // Absolute scroll in px (0 = node 1 centred). Read in draw / graphicsLayer / derivedStateOf only.
        val scrollX: () -> Float = remember(listState, itemPx) {
            { listState.firstVisibleItemIndex * itemPx + listState.firstVisibleItemScrollOffset }
        }

        // After a win (progress advanced since the last time this map was shown) glide one node forward.
        var shown by rememberSaveable { mutableStateOf(current) }
        LaunchedEffect(current) {
            if (current > shown) {
                delay(AdvanceDelayMs)
                listState.animateScrollToItem((current - 1).coerceAtLeast(0))
            }
            shown = current
        }

        // "Jump to current level" pill: 0 = current node on screen (hide), -1 / +1 = it lies left / right of the viewport.
        // Derived, so it only invalidates when the value flips, never per scroll frame.
        val focus = focusIndex(current, levels)
        val jumpDirection by remember(listState, itemPx, focus) {
            derivedStateOf {
                val d = focus * itemPx - scrollX()
                if (abs(d) <= JumpShowDistanceNodes * itemPx) 0 else if (d > 0f) 1 else -1
            }
        }
        val scope = rememberCoroutineScope()

        // World under the viewport centre (theme index); only changes when the world changes.
        val dominantWorld by remember(listState, itemPx) {
            derivedStateOf { floor((scrollX() / itemPx + 0.5f) / WorldRules.LEVELS_PER_WORLD).toInt().coerceAtLeast(0) }
        }
        // Status bar icons follow it; only changes when the boolean flips.
        val lightIcons by remember { derivedStateOf { WorldThemes.forThemeIndex(dominantWorld).lightStatusBarIcons } }
        StatusBarIconsEffect(lightIcons)
        // Report the world to the host (world-name label); emits on world change only, never per scroll frame.
        val onWorldChangeState by rememberUpdatedState(onWorldChange)
        LaunchedEffect(Unit) {
            snapshotFlow { dominantWorld }.collect { onWorldChangeState(it) }
        }

        // ---- backdrop: world pair (m-1, m) and which of them are drawn. stage = m * 3 + s (s: 0 lower, 1 blend, 2 upper).
        val stage by remember(listState, itemPx) {
            derivedStateOf {
                val p = scrollX() / itemPx
                val m = floor((p + 1.5f) / WorldRules.LEVELS_PER_WORLD).toInt().coerceAtLeast(0)
                val t = crossfadeT(p, m)
                val s = when {
                    m == 0 -> 2
                    t <= 0.001f -> 0
                    t >= 0.999f -> 2
                    else -> 1
                }
                m * 3 + s
            }
        }
        val m = stage / 3
        val s = stage % 3
        val baseWorld = if (s == 2) m else (m - 1).coerceAtLeast(0)

        ThemedBackdrop(baseWorld, scrollX, Modifier.fillMaxSize())
        if (s == 1) {
            ThemedBackdrop(
                world = m,
                scrollX = scrollX,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = crossfadeT(scrollX() / itemPx, m) },
            )
        }

        // ---- trail + nodes, in a lane slightly taller than the band
        val shape = remember(bandHeight) { trailShape(bandHeight) }
        // Lift the whole lane (trail + nodes), capped by the band's spare headroom so the PLAY tag never rides under the controls.
        val lift = remember(bandHeight) { journeyLift(bandHeight) }
        val laneModifier = Modifier.fillMaxWidth().height(bandHeight + Slack * 2).offset(y = bandTop - Slack - lift)
        TrailLayer(
            shape = shape,
            nodeCount = nodeCount,
            currentIndex = current - 1,
            scrollX = scrollX,
            modifier = laneModifier,
        )
        LazyRow(
            modifier = laneModifier,
            state = listState,
            contentPadding = PaddingValues(horizontal = sidePad),
        ) {
            items(count = nodeCount, key = { it }) { index ->
                TrailItem(
                    index = index,
                    current = current,
                    levels = levels,
                    stars = map.starsByLevel[index + 1] ?: 1,
                    shape = shape,
                    onNodeClick = onNodeClick,
                )
            }
        }
        jumpPill({ jumpDirection }) { scope.launch { listState.animateScrollToItem(focus) } }
    }
}

@Composable
private fun ThemedBackdrop(world: Int, scrollX: () -> Float, modifier: Modifier = Modifier) {
    val theme = remember(world) { WorldThemes.forThemeIndex(world) }
    WorldBackdrop(theme.background, theme.calm, scrollX, modifier)
}

/** One lazy item = one node column: the node (or tail) at the wave height for its absolute index. */
@Composable
private fun TrailItem(
    index: Int,
    current: Int,
    levels: Int,
    stars: Int,
    shape: TrailShape,
    onNodeClick: (Int) -> Unit,
) {
    Box(Modifier.width(ItemW).fillMaxHeight()) {
        val centerY = shape.midY + Slack - shape.amp * waveSign(index)
        if (index >= levels) {
            TailNode(
                theme = WorldThemes.forLevel(levels + 1),
                modifier = Modifier.align(Alignment.TopCenter).offset(y = centerY - TailRadius),
            )
            return@Box
        }
        val level = index + 1
        val boss = WorldRules.isBoss(level)
        val theme = WorldThemes.forLevel(level)
        val state = when {
            level < current -> NodeState.Done
            level == current -> NodeState.Current
            level < WorldRules.TEMP_UNLOCK_UP_TO -> NodeState.Done // TEMP review unlock (fake done, 3 stars via WorldMapUi.from)
            level <= WorldRules.TEMP_UNLOCK_UP_TO -> NodeState.Current // TEMP review unlock
            else -> NodeState.Locked
        }
        LevelNode(
            level = level,
            state = state,
            stars = stars,
            theme = theme,
            boss = boss,
            onClick = { onNodeClick(level) },
            showPlayTag = level == 100, // TEMP review unlock: only L100 shows PLAY
            modifier = Modifier.align(Alignment.TopCenter).offset(y = centerY - nodeCellHeight(boss) / 2),
        )
    }
}

/**
 * The wavy trail behind the nodes, drawn ONCE for the whole viewport (not per item, so no seams and no doubled
 * translucent halo): a single periodic sine path, shifted by the scroll modulo one period. Halo, path, progress tint
 * (up to the current node) and stitch; colours blend between the two worlds around a boundary. Clipped to
 * node 1 .. last node. All Paths / Strokes cached; the draw lambda only reads state and draws.
 */
@Composable
private fun TrailLayer(
    shape: TrailShape,
    nodeCount: Int,
    currentIndex: Int,
    scrollX: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shapeState by rememberUpdatedState(shape)
    val countState by rememberUpdatedState(nodeCount)
    val currentState by rememberUpdatedState(currentIndex)
    Box(
        modifier.drawWithCache {
            val itemPx = ItemW.toPx()
            val period = itemPx * 4f
            val amp = shapeState.amp.toPx()
            val mid = (shapeState.midY + Slack).toPx()
            val step = itemPx / 10f
            val end = size.width + period * 2f
            val wave = Path().apply {
                var x = 0f
                moveTo(0f, mid)
                while (x <= end) {
                    x += step
                    // node 0 centre is x = 0; sin(i * pi / 2) with i = x / itemPx
                    lineTo(x, mid - amp * sin(x / itemPx * HALF_PI))
                }
            }
            val edgeStyle = Stroke(width = TrailWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val coreStyle = Stroke(width = CoreWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val stitchStyle = Stroke(
                width = 3.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
            )
            onDrawBehind {
                val p = scrollX() / itemPx
                val m = floor((p + 1.5f) / WorldRules.LEVELS_PER_WORLD).toInt().coerceAtLeast(0)
                val t = crossfadeT(p, m)
                val lo = WorldThemes.forThemeIndex((m - 1).coerceAtLeast(0))
                val hi = WorldThemes.forThemeIndex(m)
                val edge = lerp(lo.roadEdge(), hi.roadEdge(), t)
                val core = lerp(lo.roadCore(), hi.roadCore(), t)
                val stitch = lerp(lo.roadStitch(), hi.roadStitch(), t)
                val accent = lerp(lo.accent, hi.accent, t).copy(alpha = 0.7f)

                val s = size.width / 2f - scrollX() // screen x of node 0's centre
                val tx = s - period * ceil(s / period) // shift in (-period, 0]: same curve, same dash phase
                val last = s + (countState - 1) * itemPx
                val currentX = s + currentState.coerceAtLeast(0) * itemPx
                clipRect(left = s, right = last) {
                    translate(left = tx) {
                        drawPath(wave, edge, style = edgeStyle)
                        drawPath(wave, core, style = coreStyle)
                    }
                    clipRect(left = s, right = currentX) {
                        translate(left = tx) { drawPath(wave, accent, style = coreStyle) }
                    }
                    translate(left = tx) { drawPath(wave, stitch, style = stitchStyle) }
                }
            }
        },
    )
}

private const val HALF_PI = 1.5707964f

// Road colours, all opaque, derived from existing theme fields. Light worlds: dark outline (starPlate solid) around a
// pale body; dark worlds (lightStatusBarIcons): pale outline around a deep body. Outline always contrasts the backdrop.
private fun WorldTheme.roadEdge(): Color =
    if (lightStatusBarIcons) lerp(pathColor, Color.White, 0.6f).copy(alpha = 1f) else starPlate.copy(alpha = 1f)

private fun WorldTheme.roadCore(): Color =
    if (lightStatusBarIcons) starPlate.copy(alpha = 1f)
    else lerp(pathColor.copy(alpha = 1f), starPlate.copy(alpha = 1f), 0.18f)

private fun WorldTheme.roadStitch(): Color = pathStitch.copy(alpha = 0.95f)
