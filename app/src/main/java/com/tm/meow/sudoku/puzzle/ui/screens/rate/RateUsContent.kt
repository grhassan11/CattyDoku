package com.tm.meow.sudoku.puzzle.ui.screens.rate

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.rating.RateUsPolicy
import com.tm.meow.sudoku.puzzle.ui.components.CatIcon
import com.tm.meow.sudoku.puzzle.ui.components.IconArt
import com.tm.meow.sudoku.puzzle.ui.components.rememberTapClick
import com.tm.meow.sudoku.puzzle.ui.theme.FredokaFamily
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors
import kotlinx.coroutines.launch

/**
 * Shared Rate us body (cat, title, subtitle, five stars). Stateless: tapping a star IS the action ([onStarSelected]).
 * Used by [RateUsDialog] and the Home exit sheet so there is one implementation.
 */
@Composable
internal fun RateUsContent(
    onStarSelected: (stars: Int) -> Unit,
    modifier: Modifier = Modifier,
    catSize: androidx.compose.ui.unit.Dp = 80.dp,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CatIcon(size = catSize)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.rate_title),
            color = GameColors.Title,
            fontFamily = FredokaFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.rate_subtitle),
            color = GameColors.Body,
            fontFamily = FredokaFamily,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 1..RateUsPolicy.MAX_STARS) {
                StarButton(index = i, filled = false, pulse = i == RateUsPolicy.MAX_STARS, onClick = { onStarSelected(i) })
            }
        }
    }
}

@Composable
private fun StarButton(index: Int, filled: Boolean, pulse: Boolean, onClick: () -> Unit) {
    val description = pluralStringResource(R.plurals.rate_star_description, index, index)
    val tap = rememberTapClick(onClick)
    val pop = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val infinite = rememberInfiniteTransition(label = "starPulse")
    val pulseValue by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "starPulseValue",
    )
    val pulseFraction = if (pulse) pulseValue else 0f
    // Blink hint shows the star filled (yellow); pulse is only true while no rating is picked.
    val showFilled = filled || pulse
    // Outer 52dp box = touch target + circular (unbounded) ripple; no star clip. Inner box scales the provided star art as-is.
    Box(
        Modifier
            .size(52.dp)
            .semantics {
                contentDescription = description
                selected = filled
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = false, radius = 26.dp), role = Role.Button) {
                scope.launch {
                    pop.animateTo(1.15f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                    pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                }
                tap()
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(IconArt.StarIsland),
            contentDescription = null,
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                    val s = pop.value * (1f + 0.15f * pulseFraction)
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.4f * pulseFraction
                },
            alpha = if (showFilled) 1f else 0.5f,
            colorFilter = if (showFilled) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
        )
    }
}
