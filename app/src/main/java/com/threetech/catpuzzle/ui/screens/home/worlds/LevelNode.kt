package com.threetech.catpuzzle.ui.screens.home.worlds

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.ui.components.IconArt
import com.threetech.catpuzzle.ui.components.IconImage
import com.threetech.catpuzzle.ui.components.ShadowedIcon
import com.threetech.catpuzzle.ui.components.rememberTapClick
import com.threetech.catpuzzle.ui.theme.FredokaFamily

// Node sizes: base 72 -> 92dp (+28%); cell and star tray scaled with it (cell 96x112 -> 122x142, stars 14 -> 18dp).
private val NodeBase = 92.dp
private const val CurrentScale = 1.2f
private const val RegularScale = 0.85f
private const val BossScale = 1.05f
private val LipRoom = 8.dp
internal val NodeCellWidth = 122.dp
internal val NodeCellHeight = 142.dp
private val StarSize = 18.dp
private val StarTrayOffset = 30.dp // was 20dp; +10dp gap between pad and stars tray (offset only, node/road unaffected)
private val PlayTagGap = 6.dp
private val CrownRoom = 18.dp
private val CrownIconSize = 26.dp // ic_crown box; art spans ~19dp tall, so it stays within CrownRoom above the pad
private val DarkInk = Color(0xFF3B2A1A)

// Locked (future) pads: fully opaque, world-independent warm slate so they read on every backdrop.
private val LockedPalette = NodePalette(Color(0xFF8E857C), Color(0xFF726A63), Color(0xFF4A433D))
private val LockedOutline = Color(0xFFE2DBD2)
private val LockedLockShadow = Color(0xFF2E2823)

/** Node cell size (layout footprint) for a node of this kind. */
internal fun nodeCellWidth(boss: Boolean): Dp = NodeCellWidth * (if (boss) BossScale else RegularScale)
internal fun nodeCellHeight(boss: Boolean): Dp = NodeCellHeight * (if (boss) BossScale else RegularScale)

/**
 * Level node: a single 3D cat paw pad (bean with two small top bumps, built from [padPath]) with the level number centred on it. Done: stars tray underneath, tappable via [onClick] (opens the level
 * sheet). Current: pulsing glow, bob, thicker ring, floating PLAY tag above, tappable via [onClick]. Locked (future):
 * flat, dimmed, lock icon, no stars, inert. [boss] = level 10 of a world: 1.05x with a crown. Content description per
 * state; the tappable node is always >= 48dp.
 */
@Composable
fun LevelNode(
    level: Int,
    state: NodeState,
    stars: Int,
    theme: WorldTheme,
    boss: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = state == NodeState.Current
    val locked = state == NodeState.Locked
    val scale = if (boss) BossScale else RegularScale
    val nodeSize = (if (current) NodeBase * CurrentScale else NodeBase) * scale
    val lipDp = if (locked) 4.dp else 6.dp
    val pal = if (locked) LockedPalette else theme.palFor(state)
    val description = when (state) {
        NodeState.Done -> stringResource(R.string.world_node_done, level, stars)
        NodeState.Current -> stringResource(R.string.world_node_current, level)
        NodeState.Locked -> stringResource(R.string.world_node_locked, level)
    }
    val tap = rememberTapClick(onClick)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press = animateFloatAsState(
        targetValue = if (pressed && !locked) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh),
        label = "nodePress",
    )
    val bobAnim = if (current) {
        rememberInfiniteTransition(label = "nodeBob").animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bob",
        )
    } else {
        null
    }
    val pressShift = Modifier.graphicsLayer {
        translationY = press.value * (lipDp.toPx() - 1.5.dp.toPx()).coerceAtLeast(0f)
    }
    // Light faces (some world palettes) get dark ink, dark faces white ink with a soft shadow.
    val lightFace = lerp(pal.top, pal.bottom, 0.5f).luminance() > 0.55f
    val pad = remember { Path() } // rebuilt in drawPad when the size changes

    Box(modifier.size(width = NodeCellWidth * scale, height = NodeCellHeight * scale), contentAlignment = Alignment.Center) {
        if (current) GlowRing(nodeSize * 1.45f, theme.accent)
        Box(
            Modifier
                .size(nodeSize)
                .graphicsLayer {
                    translationY = (bobAnim?.value ?: 0f) * 3.dp.toPx()
                }
                .semantics(mergeDescendants = true) { contentDescription = description }
        ) {
            // Tap target = the pad outline (not the empty corners of the square). Sibling, so the clip never cuts stars / crown.
            if (!locked) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(PadShape)
                        .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = tap),
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                val lip = lipDp.toPx()
                val faceH = this.size.height - LipRoom.toPx()
                val travel = press.value * (lip - 1.5.dp.toPx()).coerceAtLeast(0f)
                val w = this.size.width
                val a = 0.18f * (1f - 0.5f * press.value)
                for (i in 2 downTo 0) {
                    val g = i * 2.dp.toPx()
                    drawOval(
                        Color.Black.copy(alpha = a / (i + 1)),
                        Offset(w * 0.12f - g, faceH + lip - 5.dp.toPx() - g / 2f),
                        Size(w * 0.76f + 2f * g, 8.dp.toPx() + g),
                    )
                }
                drawPad(pad, travel, lip, faceH, pal, outline = (if (current) 4.dp else 2.5.dp).toPx(), locked = locked)
            }
            val labelOffset = nodeSize * 0.12f - 5.dp // centre of the pad body
            if (locked) {
                // Solid dark copy 1.5dp lower = contrast shadow, then the white lock on top.
                IconImage(
                    R.drawable.ic_lock,
                    size = nodeSize * 0.4f,
                    tint = LockedLockShadow,
                    modifier = Modifier.align(Alignment.Center).offset(y = labelOffset + 1.5.dp),
                )
                IconImage(
                    R.drawable.ic_lock,
                    size = nodeSize * 0.4f,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).offset(y = labelOffset),
                )
            } else {
                Text(
                    text = level.toString(),
                    color = if (lightFace) DarkInk else Color.White,
                    fontFamily = FredokaFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = (nodeSize.value * 0.32f).sp,
                    maxLines = 1,
                    style = TextStyle(
                        shadow = if (lightFace) null else Shadow(Color(0x99402010), Offset(0f, 4f), 2f),
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = labelOffset)
                        .then(pressShift)
                        .clearAndSetSemantics { },
                )
            }
            if (boss) {
                // Milestone badge (level multiple of 10): full-colour crown icon (no tint) drawn bottom-flush on the pad top (icon art has ~4dp blank below the crown, so the box overlaps the pad by 4dp).
                IconImage(
                    R.drawable.ic_crown,
                    size = CrownIconSize,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = -(CrownIconSize - 4.dp)),
                )
            }
            if (state == NodeState.Done) {
                NodeStars(
                    earned = stars,
                    plate = theme.starPlate,
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = StarTrayOffset),
                )
            }
        }
        if (current) {
            // Floating PLAY tag, hovering above the node (and above the crown on a boss node).
            val lift = nodeSize / 2 + PlayTagGap + (if (boss) CrownRoom else 0.dp)
            PlayTag(
                accent = theme.accent,
                modifier = Modifier
                    .align(Alignment.Center)
                    .layout { measurable, constraints ->
                        val p = measurable.measure(
                            constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity, maxHeight = Constraints.Infinity),
                        )
                        // zero-size anchor at the cell centre; the tag is placed up from there, never affecting layout
                        layout(0, 0) { p.place(-p.width / 2, -p.height - lift.roundToPx()) }
                    },
            )
        }
    }
}

/**
 * Small rounded "PLAY" pill with a pointer triangle underneath, in the world accent. Bobs forever via a
 * graphicsLayer lambda reading the animated value (no recomposition per frame). Decorative: the node description
 * already says "current level".
 */
@Composable
private fun PlayTag(accent: Color, modifier: Modifier = Modifier) {
    val bob = rememberInfiniteTransition(label = "playTagBob").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tagBob",
    )
    val ink = if (accent.luminance() > 0.55f) DarkInk else Color.White
    val pill = RoundedCornerShape(50)
    Column(
        modifier
            .graphicsLayer { translationY = bob.value * 3.dp.toPx() }
            .clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_node_play_tag),
            color = ink,
            fontFamily = FredokaFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            modifier = Modifier
                .background(accent, pill)
                .border(2.dp, Color.White, pill)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Canvas(Modifier.offset(y = (-1).dp).size(width = 14.dp, height = 8.dp)) {
            val tri = Path().apply {
                moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close()
            }
            drawPath(tri, accent)
        }
    }
}

/**
 * Earned-star row: ic_star_island x3 with drop shadow, unearned greyed. Sits on a dark rounded tray ([plate]) so the
 * yellow stars read on any world backdrop (>= 3:1). Middle star 1dp higher.
 */
@Composable
private fun NodeStars(earned: Int, plate: Color, modifier: Modifier = Modifier, total: Int = 3) {
    Row(
        modifier
            .background(plate, RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until total) {
            ShadowedIcon(
                res = IconArt.StarIsland,
                size = StarSize,
                greyed = i >= earned,
                modifier = if (i == 1) Modifier.offset(y = (-1).dp) else Modifier,
            )
        }
    }
}

@Composable
private fun GlowRing(diameter: Dp, color: Color) {
    val pulse = rememberInfiniteTransition(label = "nodeGlow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "pulse",
    )
    Canvas(Modifier.requiredSize(diameter)) {
        val p = pulse.value
        val half = this.size.minDimension / 2f
        val a = (1f - p) * 0.6f
        val r = half * (0.62f + 0.38f * p)
        drawCircle(color.copy(alpha = 0.18f), half * 0.62f)
        drawCircle(color.copy(alpha = a * 0.35f), r)
        drawCircle(color.copy(alpha = a), r, style = Stroke(width = 5.dp.toPx()))
    }
}

/** Unit-box paw pad outline (x, y in 0..1): wide rounded belly, narrower top, two soft bumps with a notch between. */
internal fun padPath(path: Path, w: Float, h: Float): Path = path.apply {
    reset()
    fun p(x: Float, y: Float) = Offset(x * w, y * h)
    moveTo(p(0.5f, 0.27f).x, p(0.5f, 0.27f).y)
    cubicTo(0.46f * w, 0.08f * h, 0.30f * w, 0.01f * h, 0.20f * w, 0.12f * h)
    cubicTo(0.12f * w, 0.21f * h, 0.17f * w, 0.34f * h, 0.08f * w, 0.46f * h)
    cubicTo(0.00f * w, 0.58f * h, 0.00f * w, 0.87f * h, 0.22f * w, 0.95f * h)
    cubicTo(0.36f * w, 1.00f * h, 0.64f * w, 1.00f * h, 0.78f * w, 0.95f * h)
    cubicTo(1.00f * w, 0.87f * h, 1.00f * w, 0.58f * h, 0.92f * w, 0.46f * h)
    cubicTo(0.83f * w, 0.34f * h, 0.88f * w, 0.21f * h, 0.80f * w, 0.12f * h)
    cubicTo(0.70f * w, 0.01f * h, 0.54f * w, 0.08f * h, 0.50f * w, 0.27f * h)
    close()
}

/** Pad shape for hit-testing: clip the clickable overlay to the pad, so taps in the empty corners miss. */
private val PadShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(padPath(Path(), size.width, size.height - with(density) { LipRoom.toPx() }))
}

/** Lip layer (fixed, lip colour, [lip] px lower), then the face (drops by [travel] on press) with outline + gloss. */
private fun DrawScope.drawPad(pad: Path, travel: Float, lip: Float, faceH: Float, pal: NodePalette, outline: Float, locked: Boolean) {
    padPath(pad, size.width, faceH)
    translate(top = lip) { drawPath(pad, pal.lip) }
    translate(top = travel) {
        drawPath(pad, Brush.verticalGradient(listOf(pal.top, pal.bottom), startY = 0f, endY = faceH))
        drawPath(
            pad, if (locked) LockedOutline else Color.White.copy(alpha = 0.95f),
            style = Stroke(width = outline, join = StrokeJoin.Round),
        )
        drawOval(
            Color.White.copy(alpha = 0.28f),
            Offset(size.width * 0.24f, faceH * 0.5f), Size(size.width * 0.2f, faceH * 0.1f),
        )
    }
}
