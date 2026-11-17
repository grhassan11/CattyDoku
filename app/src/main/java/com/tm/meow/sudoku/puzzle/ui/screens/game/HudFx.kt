package com.tm.meow.sudoku.puzzle.ui.screens.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.ui.components.OutlinedShadowText
import kotlinx.coroutines.launch

private val ComboTextRed = Color(0xFFE53935)

/**
 * Cosmetic combo chip ("x3") shown next to the HUD score from streak 2. Pulses on every increment (finite spring, read in
 * graphicsLayer, so the TopBar never recomposes per frame). No pulse when system animations are off.
 */
@Composable
internal fun ComboChip(streak: Int, modifier: Modifier = Modifier) {
    val animationsOn = rememberAnimationsEnabled(null)
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(streak) {
        if (!animationsOn) return@LaunchedEffect
        pulse.snapTo(1.2f + 0.2f * (ComboStyle.scale(streak) - 1f) / 0.4f) // bigger pulse at higher combo (1.2 .. 1.4)
        pulse.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
    }
    val description = stringResource(R.string.combo_description, streak)
    Box(
        modifier
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            }
            .background(Color(ComboStyle.colorArgb(streak)), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .semantics { contentDescription = description },
    ) {
        OutlinedShadowText(
            text = stringResource(R.string.combo_chip, streak),
            color = ComboTextRed, // red fill + white stroke, no shadow (all tiers)
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            outlineWidth = 1.5.dp,
            outlineColor = Color.White,
            withShadow = false,
        )
    }
}

private class ScoreLayoutCache {
    var text: String? = null
    var layout: TextLayoutResult? = null
}

private const val COUNT_UP_MS = 450

/**
 * HUD score that counts up to a new value and pulses once when it changes. The shown number is computed and painted in the
 * DRAW phase (drawBehind + a cached text layout), the pulse in graphicsLayer: no per-frame recomposition of the parent.
 * Count-up uses [OutcomeLogic.countUpValue]. A drop (penalty) snaps down with a small dip. With system animations off it is a
 * plain static number. The box is sized for the final value, the number is centred in it.
 */
@Composable
internal fun CountUpScoreText(score: Int, color: Color, modifier: Modifier = Modifier) {
    val animationsOn = rememberAnimationsEnabled(null)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = LocalTextStyle.current
    val style = remember(base, color) {
        base.merge(TextStyle(color = color, fontSize = 26.sp, fontWeight = FontWeight.Bold))
    }
    val target = remember(score, style, measurer) { measurer.measure(score.toString(), style) }
    val widthDp = with(density) { target.size.width.toDp() }
    val heightDp = with(density) { target.size.height.toDp() }

    val progress = remember { Animatable(1f) }
    val pulse = remember { Animatable(1f) }
    var from by remember { mutableIntStateOf(score) }
    var to by remember { mutableIntStateOf(score) }
    LaunchedEffect(score) {
        if (score == to) return@LaunchedEffect
        val now = from + OutcomeLogic.countUpValue(to - from, progress.value)
        if (!animationsOn || score <= now) {
            from = score
            to = score
            progress.snapTo(1f)
            if (animationsOn) {
                pulse.snapTo(0.9f)
                pulse.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
            }
            return@LaunchedEffect
        }
        from = now
        to = score
        progress.snapTo(0f)
        launch {
            pulse.snapTo(1.18f)
            pulse.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
        }
        progress.animateTo(1f, tween(COUNT_UP_MS, easing = FastOutSlowInEasing))
    }

    val cache = remember { ScoreLayoutCache() }
    val description = score.toString()
    Box(
        modifier
            .size(widthDp, heightDp)
            .semantics { contentDescription = description }
            .graphicsLayer {
                val s = pulse.value
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                val text = (from + OutcomeLogic.countUpValue(to - from, progress.value)).toString()
                var layout = cache.layout
                if (layout == null || cache.text != text) {
                    layout = measurer.measure(text, style)
                    cache.text = text
                    cache.layout = layout
                }
                drawText(
                    layout,
                    topLeft = Offset((size.width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f),
                )
            },
    )
}
