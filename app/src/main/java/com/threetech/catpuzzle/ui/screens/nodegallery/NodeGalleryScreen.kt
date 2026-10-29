// TEMP design preview - delete after node style is chosen
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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** TEMP: 5 candidate 3D node styles, each in Done / Current / Locked + a small path strip. Stateless. */
@Composable
fun NodeGalleryScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .background(GameColors.ScreenBackground)
            .systemBarsPadding(),
    ) {
        GalleryHeader(onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StyleSection("1. Paw-pad") { l, s, m -> PawPadNode(l, s, m) }
            StyleSection("2. Cat-face") { l, s, m -> CatFaceNode(l, s, m) }
            StyleSection("3. Fish-tin") { l, s, m -> FishTinNode(l, s, m) }
            StyleSection("4. Cushion") { l, s, m -> CushionNode(l, s, m) }
            StyleSection("5. Chunky coin") { l, s, m -> CoinNode(l, s, m) }
            Spacer(Modifier.height(24.dp))
        }
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
    val nodeSize = if (current) NodeBase * CurrentScale else NodeBase
    val lipDp = if (locked) 3.dp else 5.dp
    val pal = remember(state) { paletteFor(state) }
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

    Box(modifier.size(width = CellWidth, height = CellHeight), contentAlignment = Alignment.Center) {
        if (current) GlowRing(nodeSize * 1.55f)
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
private fun GlowRing(diameter: Dp) {
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
        drawCircle(GameColors.Primary.copy(alpha = 0.18f), half * 0.62f)
        drawCircle(GameColors.Primary.copy(alpha = a * 0.35f), r)
        drawCircle(GameColors.Primary.copy(alpha = a), r, style = Stroke(width = 5.dp.toPx()))
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
