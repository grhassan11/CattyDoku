// TEMP design preview - delete after node style is chosen (grep: TEMP node gallery)
package com.threetech.catpuzzle.ui.screens.nodegallery

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.ui.components.IconImage
import com.threetech.catpuzzle.ui.components.rememberPressScale
import com.threetech.catpuzzle.ui.components.rememberTapClick
import com.threetech.catpuzzle.ui.theme.FredokaFamily
import com.threetech.catpuzzle.ui.theme.GameColors
import kotlin.math.cos
import kotlin.random.Random
import kotlin.math.sin

/** Level node state in the gallery. */
enum class NodeState { Done, Current, Locked }

private val NodeBase = 72.dp
private const val CurrentScale = 1.2f
private val LipRoom = 6.dp
private val CellWidth = 96.dp
private val CellHeight = 112.dp
private val Ink = Color(0xFF4A2F2F)
private val StarYellow = GameColors.Bulb
private val StarEdge = Color(0xFFC98A00)

/* ------------------------------------------------------------------ screen */

/** TEMP node gallery: style picker + 5 themed world paths + selected-style specimen. Stateless nav. */
@Composable
fun NodeGalleryScreen(onBack: () -> Unit, modifier: Modifier = Modifier, onPlay9x9Sample: () -> Unit = {}) {
    var style by remember { mutableStateOf(NodeStyle.HexGem) }
    Column(
        modifier
            .fillMaxSize()
            .background(GameColors.ScreenBackground)
            .systemBarsPadding(),
    ) {
        GalleryHeader(onBack)
        Sample9x9Chip(onPlay9x9Sample) // TEMP 9x9 sample
        StylePicker(style) { style = it }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SectionTitle("World preview (${style.label})")
            for ((i, w) in Worlds.withIndex()) WorldCard(w, style, worldIndex = i)
            SectionTitle("Specimen")
            StyleSection(style.label) { l, s, m -> ThemedNode(style, Worlds[0].theme, s, l, m) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** TEMP 9x9 sample: opens the in-memory 9x9 play screen. Delete with the other "TEMP 9x9 sample" markers. */
@Composable
private fun Sample9x9Chip(onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(GameColors.Card)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("TEMP: Play 9x9 sample", color = GameColors.Body)
    }
}

@Composable
private fun GalleryHeader(onBack: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val scale = rememberPressScale(source)
    Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .size(48.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(GameColors.Card)
                .semantics { contentDescription = "Back" }
                .clickable(interactionSource = source, indication = ripple(), role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            IconImage(R.drawable.ic_back, size = 24.dp, tint = GameColors.Body)
        }
        Text(
            text = "Node Gallery (TEMP)",
            color = GameColors.Title,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FredokaFamily,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
        )
    }
}

@Composable
private fun StyleSection(title: String, node: @Composable (Int, NodeState, Modifier) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(GameColors.Card, RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            color = GameColors.Title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FredokaFamily,
            modifier = Modifier.padding(start = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            node(12, NodeState.Done, Modifier)
            node(13, NodeState.Current, Modifier)
            node(14, NodeState.Locked, Modifier)
        }
        PathStrip(node)
    }
}

/** 3 nodes (Done, Current, Locked) zig-zag on a thick stitched trail: preview of the journey-map read. */
@Composable
private fun PathStrip(node: @Composable (Int, NodeState, Modifier) -> Unit) {
    val stripH = 200.dp
    Box(
        Modifier
            .fillMaxWidth()
            .height(stripH)
            .background(GameColors.RuleCard, RoundedCornerShape(20.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val spare = stripH.toPx() - CellHeight.toPx()
            val cell = CellHeight.toPx()
            val yTop = spare * 0.15f + cell / 2f
            val yLow = spare * 0.85f + cell / 2f
            val x0 = w / 6f
            val x1 = w / 2f
            val x2 = w * 5f / 6f
            val path = Path().apply {
                moveTo(x0, yTop)
                cubicTo(x0 + w / 6f, yTop, x1 - w / 6f, yLow, x1, yLow)
                cubicTo(x1 + w / 6f, yLow, x2 - w / 6f, yTop, x2, yTop)
            }
            drawPath(path, GameColors.CtaCreamLip, style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round))
            drawPath(
                path,
                Color.White.copy(alpha = 0.85f),
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
                ),
            )
        }
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(0.15f))
                node(12, NodeState.Done, Modifier)
                Spacer(Modifier.weight(0.85f))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(0.85f))
                node(13, NodeState.Current, Modifier)
                Spacer(Modifier.weight(0.15f))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(0.15f))
                node(14, NodeState.Locked, Modifier)
                Spacer(Modifier.weight(0.85f))
            }
        }
    }
}

/* ------------------------------------------------------------------ shared node shell */

private class NodePalette(val top: Color, val bottom: Color, val lip: Color)

/** [travel] face drop in px (press), [lip] lip depth px, [faceH] face area height px. */
private class NodeMetrics(val travel: Float, val lip: Float, val faceH: Float, val pal: NodePalette)

private fun paletteFor(state: NodeState): NodePalette = when (state) {
    NodeState.Done -> NodePalette(
        lerp(GameColors.CtaGreenFace, Color.White, 0.28f), GameColors.CtaGreenFace, GameColors.CtaGreenLip,
    )
    NodeState.Current -> NodePalette(
        lerp(GameColors.CtaOrangeFace, Color.White, 0.28f), GameColors.CtaOrangeFace, GameColors.CtaOrangeLip,
    )
    NodeState.Locked -> NodePalette(Color(0xFFB9C6D6), Color(0xFF93A6BD), Color(0xFF7488A0))
}

/**
 * Common 3D behaviour: ground shadow, press (face drops onto lip), Current = 20% larger + pulsing glow + bob,
 * Locked = flatter lip + 60% alpha + lock icon, Done = 3 star badge. [drawNode] paints the style-specific art.
 * [numberYFraction] = number centre offset from face centre, as a fraction of node size.
 */
@Composable
private fun NodeShell(
    level: Int,
    state: NodeState,
    modifier: Modifier,
    numberYFraction: Float,
    drawNode: DrawScope.(NodeMetrics) -> Unit,
) {
    val current = state == NodeState.Current
    val locked = state == NodeState.Locked
    val ctx = LocalNodeCtx.current
    val theme = ctx.theme
    val nodeSize = (if (current) NodeBase * CurrentScale else NodeBase) * ctx.scale
    val lipDp = if (locked) 3.dp else 5.dp
    val pal = remember(state, theme) { theme?.palFor(state) ?: paletteFor(state) }
    val tap = rememberTapClick { }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh),
        label = "nodePress",
    )
    val bob: State<Float> = if (current) {
        rememberInfiniteTransition(label = "nodeBob").animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bob",
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    val pressShift = Modifier.graphicsLayer {
        translationY = press.value * (lipDp.toPx() - 1.5.dp.toPx()).coerceAtLeast(0f)
    }

    Box(modifier.size(width = CellWidth * ctx.scale, height = CellHeight * ctx.scale), contentAlignment = Alignment.Center) {
        if (current) GlowRing(nodeSize * 1.55f, theme?.accent ?: GameColors.Primary)
        Box(
            Modifier
                .size(nodeSize)
                .graphicsLayer {
                    translationY = bob.value * 3.dp.toPx()
                    alpha = if (locked) 0.6f else 1f
                }
                .semantics { contentDescription = "Level $level" }
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = tap),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val lip = lipDp.toPx()
                val faceH = this.size.height - LipRoom.toPx()
                val travel = press.value * (lip - 1.5.dp.toPx()).coerceAtLeast(0f)
                // soft ground shadow: stacked translucent ovals under the lip
                val w = this.size.width
                val a = 0.18f * (1f - 0.5f * press.value)
                for (i in 2 downTo 0) {
                    val g = i * 2.dp.toPx()
                    drawOval(
                        Color.Black.copy(alpha = a / (i + 1)),
                        Offset(w * 0.1f - g, faceH + lip - 3.dp.toPx() - g / 2f),
                        Size(w * 0.8f + 2f * g, 8.dp.toPx() + g),
                    )
                }
                drawNode(NodeMetrics(travel, lip, faceH, pal))
            }
            val labelOffset = nodeSize * numberYFraction - 3.dp
            if (locked) {
                IconImage(
                    R.drawable.ic_lock,
                    size = nodeSize * 0.3f,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).offset(y = labelOffset).then(pressShift),
                )
            } else {
                Text(
                    text = level.toString(),
                    color = Color.White,
                    fontFamily = FredokaFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (nodeSize.value * 0.32f).sp,
                    maxLines = 1,
                    style = TextStyle(
                        shadow = Shadow(Color(0x99402010), Offset(0f, 4f), 2f),
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    modifier = Modifier.align(Alignment.Center).offset(y = labelOffset).then(pressShift),
                )
            }
            if (theme != null && !locked) {
                Canvas(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp).size(22.dp)) {
                    val rad = this.size.minDimension / 2f
                    drawCircle(Color.White, rad)
                    drawCircle(theme.accent, rad - 1.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
                    drawGlyph(theme.glyph, center, 8.dp.toPx(), theme.accent)
                }
            }
            if (ctx.boss) {
                Canvas(Modifier.align(Alignment.TopCenter).offset(y = (-10).dp).size(width = 26.dp, height = 14.dp)) {
                    val cw = this.size.width
                    val ch = this.size.height
                    val crown = Path().apply {
                        moveTo(0f, ch); lineTo(0f, ch * 0.25f); lineTo(cw * 0.25f, ch * 0.6f); lineTo(cw * 0.5f, 0f)
                        lineTo(cw * 0.75f, ch * 0.6f); lineTo(cw, ch * 0.25f); lineTo(cw, ch); close()
                    }
                    drawPath(crown, StarYellow)
                    drawPath(crown, StarEdge, style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round))
                }
            }
            if (state == NodeState.Done) {
                Canvas(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 12.dp)
                        .size(width = 42.dp, height = 14.dp),
                ) {
                    val outer = 6.5.dp.toPx()
                    val inner = 2.8.dp.toPx()
                    val cy = this.size.height / 2f
                    val xs = floatArrayOf(this.size.width * 0.18f, this.size.width * 0.5f, this.size.width * 0.82f)
                    for ((i, x) in xs.withIndex()) {
                        val star = starPath(x, if (i == 1) cy - 1.dp.toPx() else cy, outer, inner)
                        drawPath(star, StarYellow)
                        drawPath(star, StarEdge, style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlowRing(diameter: Dp, color: Color = GameColors.Primary) {
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

private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float): Path {
    val path = Path()
    for (i in 0 until 10) {
        val angle = (-Math.PI / 2.0 + i * Math.PI / 5.0).toFloat()
        val r = if (i % 2 == 0) outer else inner
        val x = cx + r * cos(angle)
        val y = cy + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** Lip layer (fixed, [shapes] in lip colour, [NodeMetrics.lip] lower) then face layer (drops by travel on press). */
private fun DrawScope.drawLayered(
    m: NodeMetrics,
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

/* ------------------------------------------------------------------ 1. paw-pad */

@Composable
private fun PawPadNode(level: Int, state: NodeState, modifier: Modifier = Modifier) {
    NodeShell(level, state, modifier, numberYFraction = 0.2f) { m ->
        val w = size.width
        val fh = m.faceH
        drawLayered(
            m,
            shapes = { fill ->
                drawRoundRect(fill, Offset(w * 0.12f, fh * 0.46f), Size(w * 0.76f, fh * 0.54f), CornerRadius(fh * 0.27f))
                val toes = listOf(0.17f to 0.33f, 0.39f to 0.15f, 0.61f to 0.15f, 0.83f to 0.33f)
                for ((cx, cy) in toes) {
                    drawOval(fill, Offset(cx * w - w * 0.1f, cy * fh - fh * 0.11f), Size(w * 0.2f, fh * 0.22f))
                }
            },
            face = {
                highlightArc(Offset(w * 0.18f, fh * 0.52f), Size(w * 0.64f, fh * 0.44f))
            },
        )
    }
}

/* ------------------------------------------------------------------ 2. cat-face */

@Composable
private fun CatFaceNode(level: Int, state: NodeState, modifier: Modifier = Modifier) {
    NodeShell(level, state, modifier, numberYFraction = -0.11f) { m ->
        val w = size.width
        val fh = m.faceH
        val ear = 6.dp.toPx()
        drawLayered(
            m,
            shapes = { fill ->
                drawOval(fill, Offset(w * 0.05f, fh * 0.2f), Size(w * 0.9f, fh * 0.8f))
                for (mirror in listOf(false, true)) {
                    fun x(v: Float) = if (mirror) w * (1f - v) else w * v
                    val p = Path().apply {
                        moveTo(x(0.12f), fh * 0.55f)
                        lineTo(x(0.17f), fh * 0.04f)
                        lineTo(x(0.46f), fh * 0.26f)
                        close()
                    }
                    drawPath(p, fill)
                    drawPath(p, fill, style = Stroke(width = ear, join = StrokeJoin.Round))
                }
            },
            face = {
                // inner ears
                for (mirror in listOf(false, true)) {
                    fun x(v: Float) = if (mirror) w * (1f - v) else w * v
                    val p = Path().apply {
                        moveTo(x(0.19f), fh * 0.44f)
                        lineTo(x(0.21f), fh * 0.15f)
                        lineTo(x(0.37f), fh * 0.28f)
                        close()
                    }
                    drawPath(p, Color.White.copy(alpha = 0.35f))
                }
                val ey = fh * 0.66f
                val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                for (cx in listOf(w * 0.33f, w * 0.67f)) {
                    when (state) {
                        NodeState.Done -> drawArc(
                            Ink, 200f, 140f, false, Offset(cx - w * 0.06f, ey - fh * 0.04f), Size(w * 0.12f, fh * 0.12f),
                            style = stroke,
                        )
                        NodeState.Current -> {
                            drawCircle(Color.White, w * 0.075f, Offset(cx, ey))
                            drawCircle(Ink, w * 0.04f, Offset(cx + w * 0.01f, ey))
                            drawCircle(Color.White, w * 0.012f, Offset(cx + w * 0.02f, ey - w * 0.015f))
                        }
                        NodeState.Locked -> drawArc(
                            Ink, 20f, 140f, false, Offset(cx - w * 0.06f, ey - fh * 0.06f), Size(w * 0.12f, fh * 0.12f),
                            style = stroke,
                        )
                    }
                }
                if (state == NodeState.Done) {
                    for (cx in listOf(w * 0.2f, w * 0.8f)) {
                        drawOval(
                            Color(0xFFFF8FA3).copy(alpha = 0.55f),
                            Offset(cx - w * 0.06f, ey + fh * 0.04f), Size(w * 0.12f, fh * 0.08f),
                        )
                    }
                }
                // nose + w mouth
                drawCircle(Color(0xFFFF8FA3), w * 0.028f, Offset(w * 0.5f, fh * 0.76f))
                val mouth = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                for (dx in listOf(-0.05f, 0.05f)) {
                    drawArc(
                        Ink, 0f, 180f, false, Offset(w * (0.5f + dx) - w * 0.05f, fh * 0.76f), Size(w * 0.1f, fh * 0.09f),
                        style = mouth,
                    )
                }
                highlightArc(Offset(w * 0.1f, fh * 0.25f), Size(w * 0.8f, fh * 0.7f))
            },
        )
    }
}

/* ------------------------------------------------------------------ 3. fish-tin */

@Composable
private fun FishTinNode(level: Int, state: NodeState, modifier: Modifier = Modifier) {
    NodeShell(level, state, modifier, numberYFraction = -0.19f) { m ->
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
                // darker side body
                val shade = Color.Black.copy(alpha = 0.18f)
                drawRect(shade, Offset(x0, eh / 2f), Size(bw, bh))
                drawOval(shade, Offset(x0, bh), Size(bw, eh))
                // fish mark on the side label
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
                // lid: rim then inset face
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
}

/* ------------------------------------------------------------------ 4. cushion */

@Composable
private fun CushionNode(level: Int, state: NodeState, modifier: Modifier = Modifier) {
    NodeShell(level, state, modifier, numberYFraction = 0f) { m ->
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
                // stitched dashed border
                val s = 8.dp.toPx()
                drawRoundRect(
                    Color.White.copy(alpha = 0.8f),
                    Offset(left + s, s), Size(bw - 2f * s, fh - 2f * s), CornerRadius(fh * 0.22f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    ),
                )
                // corner button tufts
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
}

/* ------------------------------------------------------------------ 5. chunky coin */

@Composable
private fun CoinNode(level: Int, state: NodeState, modifier: Modifier = Modifier) {
    NodeShell(level, state, modifier, numberYFraction = 0f) { m ->
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
}

/* ================================================================== TEMP node gallery: themes + styles */

/** TEMP node gallery: corner glyph per world. */
private enum class WorldGlyph { Paw, Fish, Leaf, Flake, Heart, Star }

/**
 * TEMP node gallery: per-world skin. done/current/locked = 3D face palette; accent = glow ring + glyph badge;
 * path* = trail; bgTint = world card; banner* = header gradient.
 */
private data class WorldTheme(
    val name: String,
    val done: NodePalette,
    val current: NodePalette,
    val locked: NodePalette,
    val accent: Color,
    val glyph: WorldGlyph,
    val pathColor: Color,
    val pathStitch: Color,
    val bgTint: Color,
    val bannerA: Color,
    val bannerB: Color,
    val background: WorldBackground, // TEMP node gallery
) {
    fun palFor(state: NodeState): NodePalette = when (state) {
        NodeState.Done -> done
        NodeState.Current -> current
        NodeState.Locked -> locked
    }
}

/** TEMP node gallery: per-layer parallax speed factors (offset * speed). */
private const val ParallaxFar = 0.2f
private const val ParallaxMid = 0.5f
private const val ParallaxNear = 0.8f

/** TEMP node gallery: one background layer. [content] fills its parent, shifts by -parallaxOffset * [speed]. */
private class BackdropLayer(val name: String, val speed: Float, val content: @Composable (parallaxOffset: Float) -> Unit)

/** TEMP node gallery: ordered back-to-front layers + legibility [scrim] (over all layers) + trail [halo] colour. */
private class WorldBackground(val layers: List<BackdropLayer>, val scrim: Color, val halo: Color)

/** TEMP node gallery: theme + size scale + boss flag, read by [NodeShell]. */
private class NodeCtx(val theme: WorldTheme?, val scale: Float, val boss: Boolean)

private val LocalNodeCtx = staticCompositionLocalOf { NodeCtx(null, 1f, false) }

private class SampleWorld(val theme: WorldTheme, val doneCount: Int)

private fun pal(top: Long, bottom: Long, lip: Long) = NodePalette(Color(top), Color(bottom), Color(lip))

/* TEMP node gallery: world backgrounds. Declared before Worlds (top-level init order). */
private val SunnyBackground = WorldBackground(
    listOf(
        BackdropLayer("sky", 0f) { SunnySky(it) },
        BackdropLayer("far", ParallaxFar) { SunnyFar(it) },
        BackdropLayer("mid", ParallaxMid) { SunnyMid(it) },
        BackdropLayer("near", ParallaxNear) { SunnyNear(it) },
    ),
    scrim = Color.White.copy(alpha = 0.10f), halo = Color.White.copy(alpha = 0.35f),
)
private val MidnightBackground = WorldBackground(
    listOf(
        BackdropLayer("sky", 0f) { MidnightSky(it) },
        BackdropLayer("far", ParallaxFar) { MidnightFar(it) },
        BackdropLayer("mid", ParallaxMid) { MidnightMid(it) },
        BackdropLayer("near", ParallaxNear) { MidnightNear(it) },
    ),
    scrim = Color.Transparent, halo = Color(0xFF8E98D8).copy(alpha = 0.28f),
)
private val CandyBackground = WorldBackground(
    listOf(
        BackdropLayer("sky", 0f) { CandySky(it) },
        BackdropLayer("far", ParallaxFar) { CandyFar(it) },
        BackdropLayer("mid", ParallaxMid) { CandyMid(it) },
        BackdropLayer("near", ParallaxNear) { CandyNear(it) },
    ),
    scrim = Color.White.copy(alpha = 0.12f), halo = Color.White.copy(alpha = 0.40f),
)
private val SnowBackground = WorldBackground(
    listOf(
        BackdropLayer("sky", 0f) { SnowSky(it) },
        BackdropLayer("far", ParallaxFar) { SnowFar(it) },
        BackdropLayer("mid", ParallaxMid) { SnowMid(it) },
        BackdropLayer("near", ParallaxNear) { SnowNear(it) },
    ),
    scrim = Color.Transparent, halo = Color(0xFF7FB1D1).copy(alpha = 0.35f),
)
private val BerryBackground = WorldBackground(
    listOf(
        BackdropLayer("sky", 0f) { BerrySky(it) },
        BackdropLayer("far", ParallaxFar) { BerryFar(it) },
        BackdropLayer("mid", ParallaxMid) { BerryMid(it) },
        BackdropLayer("near", ParallaxNear) { BerryNear(it) },
    ),
    scrim = Color.Black.copy(alpha = 0.08f), halo = Color(0xFF2A1445).copy(alpha = 0.35f),
)

private val Worlds = listOf(
    SampleWorld(
        WorldTheme(
            "Sunny Garden", pal(0xFF8BD66A, 0xFF4CAF50, 0xFF2E7D32), pal(0xFFFFC857, 0xFFF59E0B, 0xFFB45309),
            pal(0xFFC3CFBE, 0xFF9EB096, 0xFF7F9378), Color(0xFFF59E0B), WorldGlyph.Leaf,
            Color(0xFFE8D9A8), Color.White.copy(alpha = 0.85f), Color(0xFFEAF6DC), Color(0xFF6CC24A), Color(0xFF3E9B3E),
            SunnyBackground,
        ),
        6,
    ),
    SampleWorld(
        WorldTheme(
            "Midnight Roof", pal(0xFF7C8CF8, 0xFF4F5FD6, 0xFF2F3A9A), pal(0xFFFFD166, 0xFFF4A300, 0xFFA86B00),
            pal(0xFF6B7391, 0xFF4F5675, 0xFF3A4060), Color(0xFFFFD166), WorldGlyph.Star,
            Color(0xFF3B4270), Color(0xFF8E98D8), Color(0xFF1F2547), Color(0xFF3B4BB5), Color(0xFF1B2358),
            MidnightBackground,
        ),
        3,
    ),
    SampleWorld(
        WorldTheme(
            "Candy Kitchen", pal(0xFFFF8FB8, 0xFFE8508B, 0xFFA82D60), pal(0xFF5ED4C9, 0xFF14B8A6, 0xFF0B7A6E),
            pal(0xFFD2BFCB, 0xFFB59AA9, 0xFF9A7C8E), Color(0xFF14B8A6), WorldGlyph.Heart,
            Color(0xFFF7C6D9), Color.White.copy(alpha = 0.9f), Color(0xFFFFEAF2), Color(0xFFFF7BAA), Color(0xFFE0457F),
            CandyBackground,
        ),
        9,
    ),
    SampleWorld(
        WorldTheme(
            "Snow Cabin", pal(0xFF6EC1E4, 0xFF3A97C9, 0xFF226A92), pal(0xFFFF8A65, 0xFFF4511E, 0xFFA8330F),
            pal(0xFFC4D3E0, 0xFFA2B6C8, 0xFF8499AE), Color(0xFFF4511E), WorldGlyph.Flake,
            Color(0xFFF4FAFF), Color(0xFF9CC4DE), Color(0xFFE3F1FA), Color(0xFF5AB4DD), Color(0xFF2F7FB0),
            SnowBackground,
        ),
        1,
    ),
    SampleWorld(
        WorldTheme(
            "Berry Dusk", pal(0xFFB07CE8, 0xFF8A4FD0, 0xFF5A2E96), pal(0xFFFFB454, 0xFFFB8C00, 0xFFA85A00),
            pal(0xFFCFC3DD, 0xFFB0A1C5, 0xFF8F7FAA), Color(0xFFFB8C00), WorldGlyph.Fish,
            Color(0xFFE6D8F7), Color.White.copy(alpha = 0.85f), Color(0xFFF1E9FB), Color(0xFFA066E0), Color(0xFF6C3BB0),
            BerryBackground,
        ),
        10,
    ),
)

private enum class NodeStyle(val label: String) {
    HexGem("Hex gem"), PawStamp("Paw stamp"), Yarn("Yarn ball"), FishBadge("Fish badge"),
    Cloud("Cloud"), Plank("Plank sign"), Medal("Star medal"), Candy("Candy"),
    PawPad("Paw pad (old)"), CatFace("Cat face (old)"), FishTin("Fish tin (old)"),
    Cushion("Cushion (old)"), Coin("Coin (old)"),
}

@Composable
private fun StyledNode(style: NodeStyle, level: Int, state: NodeState, modifier: Modifier) {
    when (style) {
        NodeStyle.HexGem -> HexGemNode(level, state, modifier)
        NodeStyle.PawStamp -> PawStampNode(level, state, modifier)
        NodeStyle.Yarn -> YarnNode(level, state, modifier)
        NodeStyle.FishBadge -> FishBadgeNode(level, state, modifier)
        NodeStyle.Cloud -> CloudNode(level, state, modifier)
        NodeStyle.Plank -> PlankNode(level, state, modifier)
        NodeStyle.Medal -> MedalNode(level, state, modifier)
        NodeStyle.Candy -> CandyNode(level, state, modifier)
        NodeStyle.PawPad -> PawPadNode(level, state, modifier)
        NodeStyle.CatFace -> CatFaceNode(level, state, modifier)
        NodeStyle.FishTin -> FishTinNode(level, state, modifier)
        NodeStyle.Cushion -> CushionNode(level, state, modifier)
        NodeStyle.Coin -> CoinNode(level, state, modifier)
    }
}

/** Any style x any theme. Swapping [theme] re-skins with no new drawing code. */
@Composable
private fun ThemedNode(
    style: NodeStyle,
    theme: WorldTheme,
    state: NodeState,
    level: Int,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    boss: Boolean = false,
) {
    CompositionLocalProvider(LocalNodeCtx provides NodeCtx(theme, scale, boss)) {
        StyledNode(style, level, state, modifier)
    }
}

/* ------------------------------------------------------------------ picker + world preview */

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = GameColors.Title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FredokaFamily,
        modifier = Modifier.padding(start = 4.dp).semantics { heading() },
    )
}

@Composable
private fun StylePicker(selected: NodeStyle, onSelect: (NodeStyle) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (st in NodeStyle.entries) {
            val on = st == selected
            Box(
                Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (on) GameColors.Primary else GameColors.Card)
                    .clickable(role = Role.Button) { onSelect(st) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    st.label,
                    color = if (on) Color.White else GameColors.Body,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FredokaFamily,
                )
            }
        }
    }
}

@Composable
private fun WorldCard(world: SampleWorld, style: NodeStyle, worldIndex: Int) {
    val t = world.theme
    Column(
        Modifier
            .fillMaxWidth()
            .background(t.bgTint, RoundedCornerShape(24.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WorldBanner(t, world.doneCount, worldIndex + 1)
        WorldPath(t, style, world.doneCount, baseLevel = worldIndex * 10)
    }
}

@Composable
private fun WorldBanner(t: WorldTheme, done: Int, worldNo: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(t.bannerA, t.bannerB)), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.size(34.dp)) {
                drawCircle(Color.White, this.size.minDimension / 2f)
                drawGlyph(t.glyph, center, 13.dp.toPx(), t.bannerB)
            }
            Text(
                "World $worldNo  ${t.name}",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FredokaFamily,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                "$done/10",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FredokaFamily,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color.White.copy(alpha = 0.35f), CircleShape),
        ) {
            if (done > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(done / 10f)
                        .fillMaxHeight()
                        .background(Color.White, CircleShape),
                )
            }
        }
    }
}

/** 10 nodes bottom-up on a winding trail; i < done = Done, i == done = Current, rest Locked; node 10 = boss (larger + crown). */
@Composable
private fun WorldPath(t: WorldTheme, style: NodeStyle, done: Int, baseLevel: Int) {
    val rowH = 92.dp
    val pad = 36.dp
    val total = rowH * 10 + pad
    val xs = floatArrayOf(0.5f, 0.76f, 0.5f, 0.24f, 0.5f, 0.76f, 0.5f, 0.24f, 0.5f, 0.5f)
    BoxWithConstraints(Modifier.fillMaxWidth().height(total)) {
        val wDp = maxWidth
        WorldBackdrop(t.background, parallaxOffset = 0f, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))) // TEMP node gallery
        fun cx(i: Int): Dp = wDp * xs[i]
        fun cy(i: Int): Dp = total - pad / 2 - rowH * i - rowH / 2
        Canvas(Modifier.fillMaxSize()) {
            fun pt(i: Int) = Offset(cx(i).toPx(), cy(i).toPx())
            fun trail(upTo: Int): Path = Path().apply {
                moveTo(pt(0).x, pt(0).y)
                for (i in 1..upTo) {
                    val p0 = pt(i - 1)
                    val p1 = pt(i)
                    val my = (p0.y + p1.y) / 2f
                    cubicTo(p0.x, my, p1.x, my, p1.x, p1.y)
                }
            }
            val all = trail(9)
            drawPath(all, t.background.halo, style = Stroke(width = 32.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)) // TEMP node gallery halo
            drawPath(all, t.pathColor, style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round))
            if (done > 0) {
                drawPath(trail(done.coerceAtMost(9)), t.accent.copy(alpha = 0.35f), style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round))
            }
            drawPath(
                all, t.pathStitch,
                style = Stroke(
                    width = 3.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
                ),
            )
        }
        for (i in 0 until 10) {
            val boss = i == 9
            val s = if (boss) 1.05f else 0.85f
            val state = when {
                i < done -> NodeState.Done
                i == done -> NodeState.Current
                else -> NodeState.Locked
            }
            val cellW = CellWidth * s
            val cellH = CellHeight * s
            ThemedNode(
                style, t, state, baseLevel + i + 1,
                modifier = Modifier.align(Alignment.TopStart).offset(x = cx(i) - cellW / 2, y = cy(i) - cellH / 2),
                scale = s, boss = boss,
            )
        }
    }
}

/* ------------------------------------------------------------------ glyphs */

private fun DrawScope.drawGlyph(g: WorldGlyph, c: Offset, s: Float, color: Color) {
    when (g) {
        WorldGlyph.Paw -> {
            drawOval(color, Offset(c.x - s * 0.5f, c.y - s * 0.05f), Size(s, s * 0.6f))
            for ((dx, dy) in listOf(-0.5f to -0.25f, -0.18f to -0.5f, 0.18f to -0.5f, 0.5f to -0.25f)) {
                drawCircle(color, s * 0.14f, Offset(c.x + dx * s, c.y + dy * s))
            }
        }
        WorldGlyph.Fish -> {
            drawOval(color, Offset(c.x - s * 0.6f, c.y - s * 0.3f), Size(s * 0.95f, s * 0.6f))
            val tail = Path().apply {
                moveTo(c.x + s * 0.2f, c.y); lineTo(c.x + s * 0.6f, c.y - s * 0.3f)
                lineTo(c.x + s * 0.6f, c.y + s * 0.3f); close()
            }
            drawPath(tail, color)
        }
        WorldGlyph.Leaf -> {
            val leaf = Path().apply {
                moveTo(c.x - s * 0.5f, c.y + s * 0.5f)
                quadraticTo(c.x - s * 0.6f, c.y - s * 0.5f, c.x + s * 0.5f, c.y - s * 0.5f)
                quadraticTo(c.x + s * 0.6f, c.y + s * 0.6f, c.x - s * 0.5f, c.y + s * 0.5f)
                close()
            }
            drawPath(leaf, color)
        }
        WorldGlyph.Flake -> for (k in 0 until 3) {
            rotate(k * 60f, c) {
                drawLine(color, Offset(c.x, c.y - s * 0.55f), Offset(c.x, c.y + s * 0.55f), strokeWidth = s * 0.16f, cap = StrokeCap.Round)
            }
        }
        WorldGlyph.Heart -> {
            drawCircle(color, s * 0.28f, Offset(c.x - s * 0.25f, c.y - s * 0.15f))
            drawCircle(color, s * 0.28f, Offset(c.x + s * 0.25f, c.y - s * 0.15f))
            val tri = Path().apply {
                moveTo(c.x - s * 0.5f, c.y - s * 0.02f); lineTo(c.x + s * 0.5f, c.y - s * 0.02f)
                lineTo(c.x, c.y + s * 0.55f); close()
            }
            drawPath(tri, color)
        }
        WorldGlyph.Star -> drawPath(starPath(c.x, c.y, s * 0.6f, s * 0.26f), color)
    }
}

private fun hexPath(c: Offset, r: Float): Path = Path().apply {
    for (i in 0 until 6) {
        val a = Math.toRadians(-90.0 + 60.0 * i).toFloat()
        val x = c.x + r * cos(a)
        val y = c.y + r * sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/* ------------------------------------------------------------------ N1. hex gem */

@Composable
private fun HexGemNode(level: Int, state: NodeState, modifier: Modifier = Modifier) {
    NodeShell(level, state, modifier, numberYFraction = 0f) { m ->
        val w = size.width
        val fh = m.faceH
        val r = fh / 2f
        val c = Offset(w / 2f, r)
        val hex = hexPath(c, r)
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
                drawPath(hexPath(c, r * 0.78f), Color.White.copy(alpha = 0.45f), style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))
                drawCircle(Color.White.copy(alpha = 0.85f), 2.dp.toPx(), Offset(c.x - r * 0.45f, c.y - r * 0.55f))
            },
        )
    }
}

/* ------------------------------------------------------------------ N2. paw stamp (scalloped seal) */

@Composable
private fun PawStampNode(level: Int, state: NodeState, modifier: Modifier = Modifier) {
    NodeShell(level, state, modifier, numberYFraction = 0.14f) { m ->
        val w = size.width
        val fh = m.faceH
        val r = fh / 2f * 0.98f
        val c = Offset(w / 2f, fh / 2f)
        drawLayered(
            m,
            shapes = { fill ->
                drawCircle(fill, r * 0.9f, c)
                for (k in 0 until 14) {
                    val a = (2.0 * Math.PI * k / 14.0).toFloat()
                    drawCircle(fill, r * 0.13f, Offset(c.x + r * 0.88f * cos(a), c.y + r * 0.88f * sin(a)))
                }
            },
            face = {
                drawCircle(
                    Color.White.copy(alpha = 0.55f), r * 0.7f, c,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
                drawGlyph(WorldGlyph.Paw, Offset(c.x, fh * 0.3f), r * 0.4f, Color.White.copy(alpha = 0.6f))
                highlightArc(Offset(c.x - r * 0.8f, c.y - r * 0.8f), Size(r * 1.6f, r * 1.6f))
            },
        )
    }
}

/* ------------------------------------------------------------------ N3. yarn ball */

@Composable
private fun YarnNode(level: Int, state: NodeState, modifier: Modifier = Modifier) {
    NodeShell(level, state, modifier, numberYFraction = 0f) { m ->
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
}

/* ------------------------------------------------------------------ N4. fish badge */

@Composable
private fun FishBadgeNode(level: Int, state: NodeState, modifier: Modifier = Modifier) {
    NodeShell(level, state, modifier, numberYFraction = 0f) { m ->
        val w = size.width
        val fh = m.faceH
        val tail = Path().apply {
            moveTo(w * 0.72f, fh * 0.5f); lineTo(w * 0.98f, fh * 0.16f)
            quadraticTo(w * 0.88f, fh * 0.5f, w * 0.98f, fh * 0.84f); close()
        }
        val topFin = Path().apply { moveTo(w * 0.35f, fh * 0.2f); lineTo(w * 0.5f, 0f); lineTo(w * 0.62f, fh * 0.22f); close() }
        val botFin = Path().apply { moveTo(w * 0.38f, fh * 0.8f); lineTo(w * 0.5f, fh); lineTo(w * 0.6f, fh * 0.78f); close() }
        drawLayered(
            m,
            shapes = { fill ->
                drawOval(fill, Offset(w * 0.12f, fh * 0.14f), Size(w * 0.66f, fh * 0.72f))
                drawPath(tail, fill)
                drawPath(topFin, fill)
                drawPath(botFin, fill)
            },
            face = {
                drawOval(Color.White.copy(alpha = 0.22f), Offset(w * 0.2f, fh * 0.6f), Size(w * 0.5f, fh * 0.2f))
                drawCircle(Color.White, 3.5.dp.toPx(), Offset(w * 0.2f, fh * 0.42f))
                drawCircle(Ink, 1.8.dp.toPx(), Offset(w * 0.2f, fh * 0.42f))
                drawArc(
                    Color.Black.copy(alpha = 0.2f), -70f, 140f, false, Offset(w * 0.26f, fh * 0.3f), Size(w * 0.1f, fh * 0.4f),
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                )
                drawLine(Color.White.copy(alpha = 0.35f), Offset(w * 0.8f, fh * 0.5f), Offset(w * 0.95f, fh * 0.3f), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.35f), Offset(w * 0.8f, fh * 0.5f), Offset(w * 0.95f, fh * 0.7f), 1.5.dp.toPx(), StrokeCap.Round)
                highlightArc(Offset(w * 0.18f, fh * 0.2f), Size(w * 0.5f, fh * 0.5f))
            },
        )
    }
}

/* ------------------------------------------------------------------ N5. cloud */

@Composable
private fun CloudNode(level: Int, state: NodeState, modifier: Modifier = Modifier) {
    NodeShell(level, state, modifier, numberYFraction = 0.08f) { m ->
        val w = size.width
        val fh = m.faceH
        drawLayered(
            m,
            shapes = { fill ->
                drawRoundRect(fill, Offset(w * 0.06f, fh * 0.5f), Size(w * 0.88f, fh * 0.5f), CornerRadius(fh * 0.25f))
                drawCircle(fill, w * 0.18f, Offset(w * 0.3f, fh * 0.5f))
                drawCircle(fill, w * 0.24f, Offset(w * 0.52f, fh * 0.38f))
                drawCircle(fill, w * 0.15f, Offset(w * 0.76f, fh * 0.55f))
            },
            face = {
                drawCircle(Color.White.copy(alpha = 0.3f), w * 0.1f, Offset(w * 0.45f, fh * 0.3f))
                drawCircle(Color.White.copy(alpha = 0.25f), w * 0.05f, Offset(w * 0.24f, fh * 0.44f))
                highlightArc(Offset(w * 0.28f, fh * 0.14f), Size(w * 0.48f, fh * 0.48f), 0.8f)
            },
        )
    }
}

/* ------------------------------------------------------------------ N6. plank sign */

@Composable
private fun PlankNode(level: Int, state: NodeState, modifier: Modifier = Modifier) {
    NodeShell(level, state, modifier, numberYFraction = 0f) { m ->
        val w = size.width
        val fh = m.faceH
        val sign = Path().apply {
            moveTo(w * 0.05f, fh * 0.18f); lineTo(w * 0.78f, fh * 0.18f); lineTo(w * 0.96f, fh * 0.5f)
            lineTo(w * 0.78f, fh * 0.82f); lineTo(w * 0.05f, fh * 0.82f); close()
        }
        drawLayered(
            m,
            shapes = { fill ->
                drawPath(sign, fill)
                drawPath(sign, fill, style = Stroke(width = 4.dp.toPx(), join = StrokeJoin.Round))
            },
            face = {
                for (k in 0 until 4) {
                    val y = fh * (0.28f + 0.15f * k)
                    drawLine(Color.Black.copy(alpha = 0.12f), Offset(w * 0.2f, y), Offset(w * 0.72f, y + 1.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
                }
                drawLine(Color.White.copy(alpha = 0.5f), Offset(w * 0.1f, fh * 0.23f), Offset(w * 0.76f, fh * 0.23f), 2.dp.toPx(), StrokeCap.Round)
                for (y in listOf(0.32f, 0.68f)) {
                    drawCircle(m.pal.lip, 2.5.dp.toPx(), Offset(w * 0.11f, fh * y))
                    drawCircle(Color.White.copy(alpha = 0.6f), 0.9.dp.toPx(), Offset(w * 0.105f, fh * y - 1.dp.toPx()))
                }
            },
        )
    }
}

/* ------------------------------------------------------------------ N7. star-burst medal */

@Composable
private fun MedalNode(level: Int, state: NodeState, modifier: Modifier = Modifier) {
    NodeShell(level, state, modifier, numberYFraction = 0f) { m ->
        val w = size.width
        val fh = m.faceH
        val rad = fh / 2f
        val c = Offset(w / 2f, rad)
        val burst = Path().apply {
            for (i in 0 until 24) {
                val a = (-Math.PI / 2.0 + i * Math.PI / 12.0).toFloat()
                val rr = if (i % 2 == 0) rad else rad * 0.84f
                val x = c.x + rr * cos(a)
                val y = c.y + rr * sin(a)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawLayered(
            m,
            shapes = { fill ->
                drawPath(burst, fill)
                drawPath(burst, fill, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
            },
            face = {
                drawCircle(
                    Brush.verticalGradient(listOf(lerp(m.pal.top, Color.White, 0.3f), m.pal.bottom), c.y - rad * 0.66f, c.y + rad * 0.66f),
                    rad * 0.68f, c,
                )
                drawCircle(Color.White.copy(alpha = 0.55f), rad * 0.68f, c, style = Stroke(width = 1.5.dp.toPx()))
                highlightArc(Offset(c.x - rad * 0.55f, c.y - rad * 0.55f), Size(rad * 1.1f, rad * 1.1f), 0.8f, 2.5.dp)
            },
        )
    }
}

/* ------------------------------------------------------------------ N8. candy */

@Composable
private fun CandyNode(level: Int, state: NodeState, modifier: Modifier = Modifier) {
    NodeShell(level, state, modifier, numberYFraction = 0f) { m ->
        val w = size.width
        val fh = m.faceH
        val r = fh * 0.36f
        val c = Offset(w / 2f, fh / 2f)
        val disc = Path().apply { addOval(Rect(c, r)) }
        fun fan(dir: Float) = Path().apply {
            val tipX = w * (0.5f + dir * 0.47f)
            moveTo(c.x + dir * r * 0.6f, c.y)
            lineTo(tipX, c.y - fh * 0.27f)
            quadraticTo(c.x + dir * w * 0.36f, c.y, tipX, c.y + fh * 0.27f)
            close()
        }
        val left = fan(-1f)
        val right = fan(1f)
        drawLayered(
            m,
            shapes = { fill ->
                drawPath(left, fill)
                drawPath(right, fill)
                drawCircle(fill, r, c)
            },
            face = {
                for (dir in listOf(-1f, 1f)) {
                    drawLine(Color.Black.copy(alpha = 0.15f), Offset(c.x + dir * r * 0.8f, c.y), Offset(w * (0.5f + dir * 0.42f), c.y - fh * 0.14f), 1.5.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.Black.copy(alpha = 0.15f), Offset(c.x + dir * r * 0.8f, c.y), Offset(w * (0.5f + dir * 0.42f), c.y + fh * 0.14f), 1.5.dp.toPx(), StrokeCap.Round)
                }
                clipPath(disc) {
                    for (k in -2..2) {
                        rotate(-35f, c) {
                            drawRect(Color.White.copy(alpha = 0.28f), Offset(c.x - r * 1.5f, c.y + k * r * 0.55f - r * 0.12f), Size(r * 3f, r * 0.24f))
                        }
                    }
                }
                highlightArc(Offset(c.x - r * 0.75f, c.y - r * 0.75f), Size(r * 1.5f, r * 1.5f), 0.85f, 2.5.dp)
            },
        )
    }
}

/* ================================================================== TEMP node gallery: world backgrounds */

/** TEMP node gallery: stacks layers back-to-front, then the scrim. Stateless; [parallaxOffset] = future scroll px. */
@Composable
private fun WorldBackdrop(bg: WorldBackground, parallaxOffset: Float, modifier: Modifier = Modifier) {
    Box(modifier) {
        for (layer in bg.layers) layer.content(parallaxOffset)
        Box(Modifier.fillMaxSize().background(bg.scrim))
    }
}

/** TEMP node gallery: cached Canvas layer. [build] runs on size change only (allocate Paths/Brushes there). */
private fun Modifier.bgLayer(speed: Float, parallaxOffset: Float, build: CacheDrawScope.() -> DrawScope.() -> Unit): Modifier =
    fillMaxSize().drawWithCache {
        val draw = build()
        onDrawBehind { translate(left = -parallaxOffset * speed) { draw(this) } }
    }

private fun CacheDrawScope.art(block: DrawScope.() -> Unit): DrawScope.() -> Unit = block

/** TEMP node gallery: filled sine ridge from [baseY] down to bottom. */
private fun ridgePath(w: Float, h: Float, baseY: Float, amp: Float, bumps: Float, phase: Float): Path {
    val p = Path()
    p.moveTo(0f, h)
    val steps = 48
    for (i in 0..steps) {
        val x = w * i / steps
        p.lineTo(x, baseY + amp * sin(x / w * bumps * 6.2831855f + phase))
    }
    p.lineTo(w, h)
    p.close()
    return p
}

private fun DrawScope.drawCloud(cx: Float, cy: Float, s: Float, color: Color) {
    drawRoundRect(color, Offset(cx - s, cy), Size(2f * s, s * 0.5f), CornerRadius(s * 0.25f))
    drawCircle(color, s * 0.4f, Offset(cx - s * 0.35f, cy))
    drawCircle(color, s * 0.55f, Offset(cx + s * 0.1f, cy - s * 0.1f))
}

/* ---- Sunny Garden */

@Composable
private fun SunnySky(o: Float) {
    Box(Modifier.bgLayer(0f, o) {
        val br = Brush.verticalGradient(listOf(Color(0xFF86D4F5), Color(0xFFD3F0EE), Color(0xFFEAF6DC)), 0f, size.height)
        art { drawRect(br) }
    })
}

@Composable
private fun SunnyFar(o: Float) {
    Box(Modifier.bgLayer(ParallaxFar, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val sun = Offset(w * 0.78f, 70f * u)
        val glow = Brush.radialGradient(listOf(Color(0x80FFE680), Color.Transparent), sun, 110f * u)
        val hills = ridgePath(w, h, h * 0.5f, 16f * u, 1.4f, 0.6f)
        art {
            drawCircle(glow, 110f * u, sun)
            for (k in 0 until 12) {
                val a = k * 0.5235988f
                drawLine(
                    Color(0x66FFE680),
                    Offset(sun.x + 40f * u * cos(a), sun.y + 40f * u * sin(a)),
                    Offset(sun.x + 56f * u * cos(a), sun.y + 56f * u * sin(a)),
                    3f * u, StrokeCap.Round,
                )
            }
            drawCircle(Color(0xFFFFD54F), 30f * u, sun)
            drawCircle(Color(0xFFFFF59D), 22f * u, sun)
            val cloud = Color.White.copy(alpha = 0.85f)
            drawCloud(w * 0.2f, 120f * u, 34f * u, cloud)
            drawCloud(w * 0.55f, 40f * u, 26f * u, cloud)
            drawCloud(w * 0.85f, h * 0.3f, 30f * u, cloud)
            drawCloud(w * 0.12f, h * 0.4f, 24f * u, cloud)
            drawPath(hills, Color(0xFFB5E19A))
        }
    })
}

@Composable
private fun SunnyMid(o: Float) {
    Box(Modifier.bgLayer(ParallaxMid, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val hills = ridgePath(w, h, h * 0.62f, 20f * u, 2f, 2f)
        val hedgeTop = h * 0.7f
        val spans = listOf(-0.02f to 0.3f, 0.5f to 0.78f, 0.86f to 1.02f)
        val hedges = Path().apply {
            for ((a, b) in spans) addRoundRect(RoundRect(w * a, hedgeTop, w * b, hedgeTop + 46f * u, 18f * u, 18f * u))
        }
        val rnd = Random(7)
        val n = 24
        val fx = FloatArray(n)
        val fy = FloatArray(n)
        for (i in 0 until n) {
            val (a, b) = spans[i % spans.size]
            fx[i] = w * (a + (b - a) * (0.1f + 0.8f * rnd.nextFloat()))
            fy[i] = hedgeTop + (8f + 28f * rnd.nextFloat()) * u
        }
        val petals = longArrayOf(0xFFFF8FB8, 0xFFFFF176, 0xFFFFFFFF)
        art {
            drawPath(hills, Color(0xFF8ED071))
            drawPath(hedges, Color(0xFF4F9E45))
            for (i in 0 until n) {
                drawCircle(Color(petals[i % 3]), 3.4f * u, Offset(fx[i], fy[i]))
                drawCircle(Color(0xFFFFB300), 1.3f * u, Offset(fx[i], fy[i]))
            }
        }
    })
}

@Composable
private fun SunnyNear(o: Float) {
    Box(Modifier.bgLayer(ParallaxNear, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val ground = ridgePath(w, h, h * 0.9f, 8f * u, 2.5f, 1f)
        val rnd = Random(3)
        val tufts = Path().apply {
            for (i in 0 until 16) {
                val x = w * (i + 0.5f) / 16f + (rnd.nextFloat() - 0.5f) * 10f * u
                val y = h * 0.9f + 8f * u + rnd.nextFloat() * h * 0.08f
                val bh = (12f + rnd.nextFloat() * 10f) * u
                for (b in -1..1) {
                    moveTo(x + b * 5f * u - 2.5f * u, y)
                    lineTo(x + b * 8f * u, y - bh * (if (b == 0) 1f else 0.75f))
                    lineTo(x + b * 5f * u + 2.5f * u, y)
                    close()
                }
            }
        }
        val fx = floatArrayOf(0.1f, 0.3f, 0.66f, 0.9f, 0.45f)
        val fyy = floatArrayOf(0.95f, 0.975f, 0.96f, 0.94f, 0.985f)
        val heads = longArrayOf(0xFFFF8FB8, 0xFFFFF176, 0xFFFFFFFF, 0xFFFF8FB8, 0xFFFFF176)
        art {
            drawPath(ground, Color(0xFF5FB04A))
            drawPath(tufts, Color(0xFF3F8F3A))
            for (i in fx.indices) {
                val x = w * fx[i]
                val y = h * fyy[i]
                drawLine(Color(0xFF2F7A32), Offset(x, y), Offset(x, y - 22f * u), 2f * u, StrokeCap.Round)
                drawCircle(Color(heads[i]), 6f * u, Offset(x, y - 24f * u))
                drawCircle(Color(0xFFFFB300), 2.4f * u, Offset(x, y - 24f * u))
            }
        }
    })
}

/* ---- Midnight Roof */

@Composable
private fun MidnightSky(o: Float) {
    Box(Modifier.bgLayer(0f, o) {
        val br = Brush.verticalGradient(listOf(Color(0xFF0B0F33), Color(0xFF232A6B), Color(0xFF4B4F96)), 0f, size.height)
        art { drawRect(br) }
    })
}

@Composable
private fun MidnightFar(o: Float) {
    Box(Modifier.bgLayer(ParallaxFar, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val moonC = Offset(w * 0.2f, 70f * u)
        val glow = Brush.radialGradient(listOf(Color(0x55FFF3C4), Color.Transparent), moonC, 80f * u)
        val moon = Path()
        val disc = Path().apply { addOval(Rect(moonC, 26f * u)) }
        val bite = Path().apply { addOval(Rect(Offset(moonC.x + 12f * u, moonC.y - 6f * u), 23f * u)) }
        moon.op(disc, bite, PathOperation.Difference)
        val rnd = Random(11)
        val n = 46
        val sx = FloatArray(n)
        val sy = FloatArray(n)
        val sr = FloatArray(n)
        val sa = FloatArray(n)
        for (i in 0 until n) {
            sx[i] = w * rnd.nextFloat()
            sy[i] = h * 0.55f * rnd.nextFloat()
            sr[i] = (0.7f + 1.1f * rnd.nextFloat()) * u
            sa[i] = 0.45f + 0.55f * rnd.nextFloat()
        }
        val baseY = h * 0.6f
        val skyline = Path()
        val windows = Path()
        var x = -4f * u
        while (x < w) {
            val bw = (26f + rnd.nextFloat() * 26f) * u
            val bh = (40f + rnd.nextFloat() * 80f) * u
            skyline.addRect(Rect(x, baseY - bh, x + bw, h))
            var wy = baseY - bh + 8f * u
            while (wy < baseY - 6f * u) {
                var wx = x + 5f * u
                while (wx < x + bw - 6f * u) {
                    if (rnd.nextFloat() < 0.3f) windows.addRect(Rect(wx, wy, wx + 4f * u, wy + 5f * u))
                    wx += 10f * u
                }
                wy += 14f * u
            }
            x += bw + 2f * u
        }
        art {
            drawCircle(glow, 80f * u, moonC)
            for (i in 0 until n) drawCircle(Color(0xFFFFF8E1).copy(alpha = sa[i]), sr[i], Offset(sx[i], sy[i]))
            drawPath(moon, Color(0xFFFFF3C4))
            drawPath(skyline, Color(0xFF1E2559))
            drawPath(windows, Color(0xFFFFE28A).copy(alpha = 0.55f))
        }
    })
}

@Composable
private fun MidnightMid(o: Float) {
    Box(Modifier.bgLayer(ParallaxMid, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val rnd = Random(13)
        val base = h * 0.76f
        val houses = Path()
        val glass = Path()
        var x = -10f * u
        while (x < w) {
            val bw = (60f + rnd.nextFloat() * 30f) * u
            val top = base + rnd.nextFloat() * 24f * u
            val roofH = (22f + rnd.nextFloat() * 12f) * u
            houses.addRect(Rect(x, top, x + bw, h))
            houses.moveTo(x - 4f * u, top)
            houses.lineTo(x + bw / 2f, top - roofH)
            houses.lineTo(x + bw + 4f * u, top)
            houses.close()
            glass.addRect(Rect(x + bw * 0.2f, top + 14f * u, x + bw * 0.2f + 9f * u, top + 24f * u))
            if (rnd.nextFloat() < 0.7f) glass.addRect(Rect(x + bw * 0.6f, top + 14f * u, x + bw * 0.6f + 9f * u, top + 24f * u))
            x += bw + 6f * u
        }
        art {
            drawPath(houses, Color(0xFF141A47))
            drawPath(glass, Color(0xFFFFD166).copy(alpha = 0.85f))
        }
    })
}

@Composable
private fun MidnightNear(o: Float) {
    Box(Modifier.bgLayer(ParallaxNear, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val roof = Path().apply {
            moveTo(0f, h * 0.93f); lineTo(w * 0.6f, h * 0.9f); lineTo(w, h * 0.94f); lineTo(w, h); lineTo(0f, h); close()
        }
        val chimneyX = w * 0.82f
        val chimneyY = h * 0.922f
        val antX = w * 0.12f
        val antY = h * 0.924f
        art {
            drawRect(Color(0xFF1A2050), Offset(chimneyX - 13f * u, chimneyY - 40f * u), Size(26f * u, 6f * u))
            drawRect(Color(0xFF0C1030), Offset(chimneyX - 10f * u, chimneyY - 34f * u), Size(20f * u, 40f * u))
            drawLine(Color(0xFF0C1030), Offset(antX, antY), Offset(antX, antY - 46f * u), 2.5f * u, StrokeCap.Round)
            for (k in 0 until 3) {
                val y = antY - (14f + 12f * k) * u
                val half = (13f - 3f * k) * u
                drawLine(Color(0xFF0C1030), Offset(antX - half, y), Offset(antX + half, y), 2f * u, StrokeCap.Round)
            }
            drawPath(roof, Color(0xFF0C1030))
        }
    })
}

/* ---- Candy Kitchen */

@Composable
private fun CandySky(o: Float) {
    Box(Modifier.bgLayer(0f, o) {
        val br = Brush.verticalGradient(listOf(Color(0xFFFFD3E4), Color(0xFFFFE9EF), Color(0xFFFFF5E6)), 0f, size.height)
        art { drawRect(br) }
    })
}

@Composable
private fun CandyFar(o: Float) {
    Box(Modifier.bgLayer(ParallaxFar, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val rnd = Random(17)
        val jarColors = longArrayOf(0xFFFFF3B0, 0xFFB8F0E0, 0xFFD9C2FF, 0xFFFFC9A8, 0xFFBFE3FF)
        val shelves = floatArrayOf(0.22f, 0.5f)
        val jx = FloatArray(10)
        for (i in jx.indices) jx[i] = w * ((i % 5) * 0.2f + 0.04f + 0.08f * rnd.nextFloat())
        art {
            for ((si, sy) in shelves.withIndex()) {
                val y = h * sy
                for (k in 0 until 5) {
                    val jw = (26f + 6f * ((k + si) % 3)) * u
                    val jh = (34f + 8f * ((k + si * 2) % 3)) * u
                    val x = jx[si * 5 + k]
                    val c = Color(jarColors[(k + si * 2) % 5])
                    drawRoundRect(c.copy(alpha = 0.9f), Offset(x, y - jh), Size(jw, jh), CornerRadius(7f * u))
                    drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(x + 4f * u, y - jh + 6f * u), Size(3f * u, jh * 0.5f), CornerRadius(1.5f * u))
                    drawRoundRect(Color(0xFFFF8FB8), Offset(x + 3f * u, y - jh - 6f * u), Size(jw - 6f * u, 7f * u), CornerRadius(3f * u))
                }
                drawRect(Color(0xFFF2B8CD), Offset(0f, y), Size(w, 6f * u))
            }
        }
    })
}

@Composable
private fun CandyMid(o: Float) {
    Box(Modifier.bgLayer(ParallaxMid, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val rnd = Random(19)
        val ns = 70
        val ax = FloatArray(ns)
        val ay = FloatArray(ns)
        val bx = FloatArray(ns)
        val bY = FloatArray(ns)
        val sc = longArrayOf(0xFFFF6FA5, 0xFF4FD1C5, 0xFFFFD54F, 0xFFA78BFA, 0xFFFFFFFF)
        for (i in 0 until ns) {
            ax[i] = w * rnd.nextFloat()
            ay[i] = h * 0.75f * rnd.nextFloat()
            val a = rnd.nextFloat() * 6.2831855f
            bx[i] = ax[i] + 6f * u * cos(a)
            bY[i] = ay[i] + 6f * u * sin(a)
        }
        val base = h * 0.8f
        val cx = floatArrayOf(0.1f, 0.3f, 0.55f, 0.78f, 0.94f)
        val cw = floatArrayOf(52f, 60f, 48f, 56f, 46f)
        val wrappers = List(cx.size) { i ->
            val c = w * cx[i]
            val half = cw[i] * u / 2f
            Path().apply {
                moveTo(c - half, base - 20f * u); lineTo(c + half, base - 20f * u)
                lineTo(c + half * 0.75f, base + 14f * u); lineTo(c - half * 0.75f, base + 14f * u); close()
            }
        }
        val frost = longArrayOf(0xFFFF8FB8, 0xFF9AE6D8, 0xFFFFE08A, 0xFFD9C2FF, 0xFFFF8FB8)
        art {
            for (i in 0 until ns) drawLine(Color(sc[i % 5]).copy(alpha = 0.75f), Offset(ax[i], ay[i]), Offset(bx[i], bY[i]), 2.4f * u, StrokeCap.Round)
            for (i in cx.indices) {
                val c = w * cx[i]
                val r = cw[i] * u / 2f
                drawPath(wrappers[i], Color(0xFFFFC9A8))
                drawLine(Color(0xFFE9A98A), Offset(c - r * 0.3f, base - 20f * u), Offset(c - r * 0.22f, base + 14f * u), 2f * u)
                drawLine(Color(0xFFE9A98A), Offset(c + r * 0.3f, base - 20f * u), Offset(c + r * 0.22f, base + 14f * u), 2f * u)
                val f = Color(frost[i])
                drawCircle(f, r * 0.55f, Offset(c - r * 0.5f, base - 24f * u))
                drawCircle(f, r * 0.55f, Offset(c + r * 0.5f, base - 24f * u))
                drawCircle(f, r * 0.7f, Offset(c, base - 32f * u))
                drawCircle(f, r * 0.45f, Offset(c, base - 46f * u))
                drawCircle(Color.White.copy(alpha = 0.45f), r * 0.14f, Offset(c - r * 0.25f, base - 36f * u))
                drawCircle(Color(0xFFE53950), 4.5f * u, Offset(c, base - 54f * u))
            }
        }
    })
}

@Composable
private fun CandyNear(o: Float) {
    Box(Modifier.bgLayer(ParallaxNear, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val y = h * 0.93f
        art {
            drawRect(Color(0xFFFFF8EE), Offset(0f, y), Size(w, h - y))
            drawRect(Color(0xFFFF8FB8), Offset(0f, y), Size(w, 10f * u))
            var x = 0f
            while (x < w + 16f * u) {
                drawCircle(Color(0xFFFF8FB8), 8f * u, Offset(x, y + 10f * u))
                x += 16f * u
            }
            drawRect(Color(0xFFFFD3E4), Offset(0f, y + 26f * u), Size(w, 3f * u))
        }
    })
}

/* ---- Snow Cabin */

@Composable
private fun SnowSky(o: Float) {
    Box(Modifier.bgLayer(0f, o) {
        val br = Brush.verticalGradient(listOf(Color(0xFFBFE1F7), Color(0xFFE6F3FC), Color(0xFFF4FAFF)), 0f, size.height)
        art { drawRect(br) }
    })
}

@Composable
private fun SnowFar(o: Float) {
    Box(Modifier.bgLayer(ParallaxFar, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val base = h * 0.52f
        val px = floatArrayOf(0.15f, 0.45f, 0.8f)
        val ph = floatArrayOf(90f, 120f, 100f)
        val mount = Path().apply {
            moveTo(0f, h); lineTo(0f, base)
            for (i in px.indices) {
                lineTo(w * px[i] - 80f * u, base)
                lineTo(w * px[i], base - ph[i] * u)
                lineTo(w * px[i] + 80f * u, base)
            }
            lineTo(w, base); lineTo(w, h); close()
        }
        val caps = Path().apply {
            for (i in px.indices) {
                val tipY = base - ph[i] * u
                moveTo(w * px[i], tipY)
                lineTo(w * px[i] - 26f * u, tipY + 34f * u)
                lineTo(w * px[i] + 26f * u, tipY + 34f * u)
                close()
            }
        }
        val pines = Path().apply {
            var x = 8f * u
            while (x < w) {
                moveTo(x - 9f * u, h * 0.58f); lineTo(x, h * 0.58f - 30f * u); lineTo(x + 9f * u, h * 0.58f); close()
                x += 28f * u
            }
        }
        art {
            drawPath(mount, Color(0xFFCFE3F2))
            drawPath(caps, Color.White.copy(alpha = 0.9f))
            drawRect(Color(0xFFDCEBF6), Offset(0f, h * 0.58f), Size(w, h * 0.42f))
            drawPath(pines, Color(0xFFA9C8DE))
        }
    })
}

@Composable
private fun SnowMid(o: Float) {
    Box(Modifier.bgLayer(ParallaxMid, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val hill = ridgePath(w, h, h * 0.73f, 12f * u, 1.6f, 0.4f)
        val rnd = Random(23)
        val pines = Path()
        val caps = Path()
        val trunks = Path()
        for (i in 0 until 9) {
            val cx = w * (i + 0.3f + rnd.nextFloat() * 0.4f) / 9f
            val ph = (70f + rnd.nextFloat() * 40f) * u
            val by = h * 0.76f + rnd.nextFloat() * 14f * u
            val pw = ph * 0.5f
            trunks.addRect(Rect(cx - 3f * u, by - 6f * u, cx + 3f * u, by + 8f * u))
            for (t in 0 until 3) {
                val ty = by - ph * (0.1f + t * 0.28f)
                val tw = pw * (1f - t * 0.28f)
                val tip = ty - ph * 0.42f
                pines.moveTo(cx - tw / 2f, ty); pines.lineTo(cx, tip); pines.lineTo(cx + tw / 2f, ty); pines.close()
                val cy = tip + (ty - tip) * 0.38f
                caps.moveTo(cx, tip); caps.lineTo(cx - tw * 0.19f, cy); caps.lineTo(cx + tw * 0.19f, cy); caps.close()
            }
        }
        art {
            drawPath(hill, Color(0xFFE8F3FA))
            drawPath(trunks, Color(0xFF6B4A3A))
            drawPath(pines, Color(0xFF4F7F9E))
            drawPath(caps, Color.White)
        }
    })
}

@Composable
private fun SnowNear(o: Float) {
    Box(Modifier.bgLayer(ParallaxNear, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val back = ridgePath(w, h, h * 0.935f, 6f * u, 2f, 2f)
        val drift = ridgePath(w, h, h * 0.9f, 10f * u, 1.5f, 0.3f)
        val cx = w * 0.17f
        val cb = h * 0.915f
        val roof = Path().apply {
            moveTo(cx - 52f * u, cb - 40f * u); lineTo(cx, cb - 74f * u); lineTo(cx + 52f * u, cb - 40f * u); close()
        }
        val snowRoof = Path().apply {
            moveTo(cx - 52f * u, cb - 40f * u); lineTo(cx, cb - 74f * u); lineTo(cx + 52f * u, cb - 40f * u)
            lineTo(cx + 44f * u, cb - 44f * u); lineTo(cx, cb - 66f * u); lineTo(cx - 44f * u, cb - 44f * u); close()
        }
        val rnd = Random(29)
        val n = 70
        val fx = FloatArray(n)
        val fy = FloatArray(n)
        val fr = FloatArray(n)
        for (i in 0 until n) {
            fx[i] = w * rnd.nextFloat()
            fy[i] = h * rnd.nextFloat()
            fr[i] = (1f + 1.8f * rnd.nextFloat()) * u
        }
        art {
            drawPath(back, Color(0xFFE3F0F8))
            drawRect(Color(0xFF5A3822), Offset(cx + 22f * u, cb - 70f * u), Size(10f * u, 24f * u))
            drawRect(Color(0xFF8A5A3B), Offset(cx - 44f * u, cb - 40f * u), Size(88f * u, 44f * u))
            drawRect(Color(0xFF5A3822), Offset(cx - 9f * u, cb - 22f * u), Size(18f * u, 26f * u))
            drawRect(Color(0xFFFFD98A), Offset(cx - 36f * u, cb - 30f * u), Size(14f * u, 14f * u))
            drawRect(Color(0xFFFFD98A), Offset(cx + 22f * u, cb - 30f * u), Size(14f * u, 14f * u))
            drawPath(roof, Color(0xFF5A3822))
            drawPath(snowRoof, Color.White)
            drawPath(drift, Color.White)
            for (i in 0 until n) drawCircle(Color.White.copy(alpha = 0.85f), fr[i], Offset(fx[i], fy[i]))
        }
    })
}

/* ---- Berry Dusk */

@Composable
private fun BerrySky(o: Float) {
    Box(Modifier.bgLayer(0f, o) {
        val br = Brush.verticalGradient(
            listOf(Color(0xFF3A2766), Color(0xFF8E4A9E), Color(0xFFFF9B6A), Color(0xFF9B4F8F)),
            0f, size.height,
        )
        art { drawRect(br) }
    })
}

@Composable
private fun BerryFar(o: Float) {
    Box(Modifier.bgLayer(ParallaxFar, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val rnd = Random(31)
        val n = 22
        val sx = FloatArray(n)
        val sy = FloatArray(n)
        for (i in 0 until n) {
            sx[i] = w * rnd.nextFloat()
            sy[i] = h * 0.3f * rnd.nextFloat()
        }
        val far1 = ridgePath(w, h, h * 0.58f, 24f * u, 1.3f, 0.2f)
        val far2 = ridgePath(w, h, h * 0.64f, 18f * u, 2.1f, 1.7f)
        art {
            for (i in 0 until n) drawCircle(Color.White.copy(alpha = 0.5f), 1.2f * u, Offset(sx[i], sy[i]))
            drawPath(far1, Color(0xFF6A3585))
            drawPath(far2, Color(0xFF55296F))
        }
    })
}

@Composable
private fun BerryMid(o: Float) {
    Box(Modifier.bgLayer(ParallaxMid, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val vines = Path()
        val vbx = FloatArray(16)
        val vby = FloatArray(16)
        var vn = 0
        for ((fx, len) in listOf(0.08f to 150f, 0.3f to 110f, 0.62f to 130f, 0.9f to 170f)) {
            val x0 = w * fx
            val total = len * u
            vines.moveTo(x0, 0f)
            for (s in 1..12) {
                val t = s / 12f
                val x = x0 + 14f * u * sin(t * 7.5f)
                val y = total * t
                vines.lineTo(x, y)
                if (s % 3 == 0 && vn < vbx.size) {
                    vbx[vn] = x
                    vby[vn] = y + 4f * u
                    vn++
                }
            }
        }
        val vineCount = vn
        val rnd = Random(37)
        val base = h * 0.74f
        val bushN = 12
        val bx = FloatArray(bushN)
        val br = FloatArray(bushN)
        for (i in 0 until bushN) {
            bx[i] = w * (i + 0.2f + 0.6f * rnd.nextFloat()) / bushN
            br[i] = (20f + 14f * rnd.nextFloat()) * u
        }
        val berryN = 30
        val ex = FloatArray(berryN)
        val ey = FloatArray(berryN)
        for (i in 0 until berryN) {
            val b = i % bushN
            ex[i] = bx[b] + (rnd.nextFloat() - 0.5f) * br[b] * 1.2f
            ey[i] = base - br[b] * 0.4f + (rnd.nextFloat() - 0.5f) * br[b]
        }
        art {
            drawRect(Color(0xFF4A2370), Offset(0f, base), Size(w, h - base))
            for (i in 0 until bushN) drawCircle(Color(0xFF4A2370), br[i], Offset(bx[i], base - br[i] * 0.4f))
            for (i in 0 until berryN) {
                drawCircle(Color(0xFFFF5C9A), 4f * u, Offset(ex[i], ey[i]))
                drawCircle(Color.White.copy(alpha = 0.7f), 1.1f * u, Offset(ex[i] - 1.2f * u, ey[i] - 1.4f * u))
            }
            drawPath(vines, Color(0xFF3E6B4A), style = Stroke(width = 3f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            for (i in 0 until vineCount) {
                drawCircle(Color(0xFFE0457F), 4.5f * u, Offset(vbx[i], vby[i]))
                drawCircle(Color.White.copy(alpha = 0.7f), 1.2f * u, Offset(vbx[i] - 1.3f * u, vby[i] - 1.5f * u))
            }
        }
    })
}

@Composable
private fun BerryNear(o: Float) {
    Box(Modifier.bgLayer(ParallaxNear, o) {
        val w = size.width
        val h = size.height
        val u = 1.dp.toPx()
        val ground = ridgePath(w, h, h * 0.93f, 6f * u, 2f, 0.5f)
        val rnd = Random(41)
        val fn = 18
        val fx = FloatArray(fn)
        val fy = FloatArray(fn)
        for (i in 0 until fn) {
            fx[i] = w * rnd.nextFloat()
            fy[i] = h * (0.2f + 0.75f * rnd.nextFloat())
        }
        val corner = floatArrayOf(0.02f, 0.09f, 0.16f, 0.98f, 0.91f, 0.84f)
        val cr = floatArrayOf(26f, 20f, 14f, 28f, 22f, 15f)
        art {
            drawPath(ground, Color(0xFF23103B))
            for (i in corner.indices) {
                val c = Offset(w * corner[i], h * 0.965f)
                drawCircle(Color(0xFF2E1650), cr[i] * u, c)
                drawCircle(Color(0xFFFF5C9A), 3.6f * u, Offset(c.x - cr[i] * 0.3f * u, c.y - cr[i] * 0.3f * u))
                drawCircle(Color(0xFFFF5C9A), 3.6f * u, Offset(c.x + cr[i] * 0.35f * u, c.y + cr[i] * 0.1f * u))
            }
            val fly = Color(0xFFFFF59D)
            for (i in 0 until fn) {
                val p = Offset(fx[i], fy[i])
                drawCircle(fly.copy(alpha = 0.12f), 10f * u, p)
                drawCircle(fly.copy(alpha = 0.28f), 5.5f * u, p)
                drawCircle(fly, 2f * u, p)
            }
        }
    })
}
