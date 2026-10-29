package com.threetech.catpuzzle.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.threetech.catpuzzle.ui.theme.GameColors

/** Colour set for a 3D button: [face] on top of a darker [lip]; [content] = text/icon colour. */
enum class ButtonStyle(val face: Color, val lip: Color, val content: Color, val textShadow: Boolean) {
    Primary(GameColors.CtaOrangeFace, GameColors.CtaOrangeLip, Color.White, true),
    Secondary(GameColors.CtaCreamFace, GameColors.CtaCreamLip, GameColors.Title, false),
    Locked(GameColors.CtaGreyFace, GameColors.CtaGreyLip, Color.White, true),
    Green(GameColors.CtaGreenFace, GameColors.CtaGreenLip, Color.White, true),
    Blue(GameColors.CtaBlueFace, GameColors.CtaBlueLip, Color.White, true),
    Pink(GameColors.DailyPink, GameColors.DailyPinkDark, Color.White, true),
}

/** Pure press math, unit-testable. [progress] 0 = rest, 1 = fully pressed. */
object Button3DMath {
    /** Face downward travel in px; at progress 1 the face sits [lipPx] - [restLipPx] lower. */
    fun faceOffset(progress: Float, lipPx: Float, restLipPx: Float = 1.5f): Float =
        progress.coerceIn(0f, 1f) * (lipPx - restLipPx).coerceAtLeast(0f)

    /** Drop-shadow alpha: full at rest, ~30% when pressed. */
    fun shadowAlpha(progress: Float, restAlpha: Float = 0.22f): Float =
        restAlpha * (1f - 0.7f * progress.coerceIn(0f, 1f))
}

private val LipHeight = 6.dp
private val FaceMinHeight = 56.dp

/** Total reserved height of a [Button3D] (face + lip). Use for fixed-height slots. */
val Button3DHeight: Dp = FaceMinHeight + LipHeight

/**
 * Pill button with a darker lip under a lighter face. Pressed: face travels down over the lip,
 * shadow shrinks, face darkens slightly. Layout never shifts (lip space is always reserved).
 * Clickable stays live when [markDisabled] (semantics-only) so callers can show "Coming soon".
 */
@Composable
fun Button3D(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    contentDescription: String? = null,
    markDisabled: Boolean = false,
    horizontalPadding: Dp = 24.dp,
    totalHeight: Dp = Button3DHeight,
    lipHeight: Dp = LipHeight,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val tapClick = rememberTapClick(onClick) // shared UI tap cue: covers PillButton, Home CTAs, outcome dialogs
    val pressed by source.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh),
        label = "button3dPress",
    )
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = totalHeight)
            .drawBehind {
                val lip = lipHeight.toPx()
                val travel = Button3DMath.faceOffset(progress, lip)
                val faceH = size.height - lip
                val r = CornerRadius(faceH / 2f)
                val faceSize = Size(size.width, faceH)
                // soft shadow: stacked translucent pills under the lip
                val a = Button3DMath.shadowAlpha(progress)
                for (i in 3 downTo 1) {
                    val grow = i * 1.2.dp.toPx()
                    drawRoundRect(
                        Color.Black.copy(alpha = a / (i + 1)),
                        Offset(-grow / 2f, lip + 1.dp.toPx() - grow / 2f),
                        Size(size.width + grow, faceH + grow),
                        CornerRadius(faceH / 2f + grow / 2f),
                    )
                }
                drawRoundRect(style.lip, Offset(0f, lip), faceSize, r)
                val faceTop = Offset(0f, travel)
                val face = androidx.compose.ui.graphics.lerp(style.face, style.lip, 0.15f * progress)
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(androidx.compose.ui.graphics.lerp(face, Color.White, 0.18f), face),
                        startY = travel,
                        endY = travel + faceH,
                    ),
                    faceTop, faceSize, r,
                )
                val inset = 1.5.dp.toPx()
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                        startY = travel,
                        endY = travel + faceH * 0.6f,
                    ),
                    Offset(inset, travel + inset),
                    Size(size.width - 2 * inset, faceH - 2 * inset),
                    CornerRadius((faceH - 2 * inset) / 2f),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            .semantics(mergeDescendants = true) {
                if (contentDescription != null) this.contentDescription = contentDescription
                if (markDisabled) disabled()
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = tapClick)
            .padding(start = horizontalPadding, end = horizontalPadding, bottom = lipHeight)
            .graphicsLayer { translationY = Button3DMath.faceOffset(progress, lipHeight.toPx()) },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
