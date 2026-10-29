package com.threetech.catpuzzle.ui.components

import com.threetech.catpuzzle.ui.theme.FredokaFamily
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.ui.theme.GameColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Scale factor (1 -> [pressedScale]) that follows the pressed state of [source]. Apply with graphicsLayer. */
@Composable
fun rememberPressScale(source: InteractionSource, pressedScale: Float = 0.94f): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return scale
}

/**
 * Rounded pill button, 56dp tall (>= 48dp touch target), press-scale feedback.
 * [filled] = orange fill + white text; otherwise outlined on a white surface.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    leadingIconRes: Int? = null,
    textSize: TextUnit = 22.sp,
    horizontalPadding: Dp = 24.dp,
    style: ButtonStyle = if (filled) ButtonStyle.Primary else ButtonStyle.Secondary,
) {
    val content = style.content
    Button3D(
        onClick = onClick,
        modifier = modifier,
        style = style,
        horizontalPadding = horizontalPadding,
    ) {
        if (leadingIconRes != null) IconImage(leadingIconRes, size = 22.dp, tint = content)
        Text(
            text,
            color = content,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FredokaFamily,
            maxLines = 1,
            style = if (style.textShadow) {
                androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(Color(0x66000000), Offset(0f, 2f), 3f),
                )
            } else {
                androidx.compose.ui.text.TextStyle.Default
            },
        )
    }
}

/** Full-screen dim layer that swallows touches so the board underneath cannot be tapped. */
@Composable
fun DialogScrim(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    scrimColor: Color = GameColors.Scrim,
    content: @Composable () -> Unit,
) {
    val absorb = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxSize()
            .background(scrimColor)
            .clickable(interactionSource = absorb, indication = null, enabled = true, onClick = onClick ?: {}),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Slowly rotating light rays behind the mascot. Pure Canvas, no assets. */
@Composable
fun Sunburst(modifier: Modifier = Modifier, color: Color = GameColors.Sunburst) {
    val transition = rememberInfiniteTransition(label = "sunburst")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing)),
        label = "sunburstAngle",
    )
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        val rays = 14
        val half = (PI / rays / 2.0).toFloat()
        val brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0f)),
            center = c,
            radius = radius,
        )
        rotate(angle, c) {
            for (i in 0 until rays) {
                val a = (2.0 * PI * i / rays).toFloat()
                val ray = Path().apply {
                    moveTo(c.x, c.y)
                    lineTo(c.x + radius * cos(a - half), c.y + radius * sin(a - half))
                    lineTo(c.x + radius * cos(a + half), c.y + radius * sin(a + half))
                    close()
                }
                drawPath(ray, brush)
            }
        }
    }
}

private class ConfettiPiece(
    val x: Float,
    val phase: Float,
    val speed: Float,
    val size: Float,
    val spin: Float,
    val color: Color,
)

private val ConfettiColors = listOf(
    Color(0xFFFF6FA5),
    Color(0xFF6BBBE8),
    Color(0xFFFFD84D),
    Color(0xFFADDB93),
    Color(0xFFF2936B),
    Color(0xFF977FD9),
)

/** Light confetti rain: [count] deterministic pieces looping top to bottom. */
@Composable
fun Confetti(modifier: Modifier = Modifier, count: Int = 30) {
    val pieces = remember(count) {
        val rnd = Random(7)
        List(count) {
            ConfettiPiece(
                x = rnd.nextFloat(),
                phase = rnd.nextFloat(),
                speed = 0.6f + rnd.nextFloat() * 0.8f,
                size = 6f + rnd.nextFloat() * 6f,
                spin = 90f + rnd.nextFloat() * 360f,
                color = ConfettiColors[rnd.nextInt(ConfettiColors.size)],
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "confetti")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5_000, easing = LinearEasing), RepeatMode.Restart),
        label = "confettiTime",
    )
    Canvas(modifier.fillMaxSize()) {
        for (p in pieces) {
            val progress = (t * p.speed + p.phase) % 1f
            val cx = p.x * size.width + sin((progress * 6f + p.phase * 6f).toDouble()).toFloat() * 14f
            val cy = progress * (size.height + 40f) - 20f
            val side = p.size * density
            rotate(progress * p.spin, Offset(cx, cy)) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(cx - side / 2f, cy - side / 4f),
                    size = Size(side, side / 2f),
                )
            }
        }
    }
}
