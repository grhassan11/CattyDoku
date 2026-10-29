package com.threetech.catpuzzle.ui.screens.rate

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.threetech.catpuzzle.ui.components.rememberTapClick
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.rating.RateUsPolicy
import com.threetech.catpuzzle.ui.components.ButtonStyle
import com.threetech.catpuzzle.ui.components.CatIcon
import com.threetech.catpuzzle.ui.components.DialogScrim
import com.threetech.catpuzzle.ui.components.IconArt
import com.threetech.catpuzzle.ui.components.PillButton
import com.threetech.catpuzzle.ui.theme.FredokaFamily
import com.threetech.catpuzzle.ui.theme.GameColors

/**
 * Own "Rate us" dialog: five tappable stars + Submit + Not now. Stateless apart from the picked star count (pure UI
 * selection state, survives rotation). 4-5 stars -> store review, 1-3 -> feedback email; routing lives in the ViewModel.
 */
@Composable
internal fun RateUsDialog(
    onSubmit: (stars: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var stars by rememberSaveable { mutableIntStateOf(0) }
    val title = stringResource(R.string.rate_title)
    BackHandler(onBack = onDismiss)

    DialogScrim(Modifier.semantics { paneTitle = title }, onClick = onDismiss) {
        val absorb = remember { MutableInteractionSource() }
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .background(GameColors.Card, RoundedCornerShape(28.dp))
                // Taps inside the card must not reach the scrim (which dismisses).
                .clickable(interactionSource = absorb, indication = null, onClick = {})
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CatIcon(size = 80.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
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
                    StarButton(index = i, filled = i <= stars, onClick = { stars = i })
                }
            }
            Spacer(Modifier.height(20.dp))
            PillButton(
                text = stringResource(R.string.rate_submit),
                onClick = { if (stars > 0) onSubmit(stars) },
                textSize = 18.sp,
                horizontalPadding = 16.dp,
                style = if (stars > 0) ButtonStyle.Green else ButtonStyle.Locked,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.rate_not_now),
                    color = GameColors.Body,
                    fontFamily = FredokaFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** 5-point star outline (outer radius = half the box, inner 0.45) so ripple + clip follow the star. */
private val StarShape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outer = size.minDimension / 2f
    val inner = outer * 0.45f
    for (k in 0 until 10) {
        val r = if (k % 2 == 0) outer else inner
        val a = -PI / 2.0 + k * PI / 5.0
        val x = cx + (r * cos(a)).toFloat()
        val y = cy + (r * sin(a)).toFloat()
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
private fun StarButton(index: Int, filled: Boolean, onClick: () -> Unit) {
    val description = pluralStringResource(R.plurals.rate_star_description, index, index)
    val tap = rememberTapClick(onClick)
    val pop = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    // Outer box = 52dp touch target, no indication; inner star-clipped box owns the bounded ripple.
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(46.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clip(StarShape)
                .semantics {
                    contentDescription = description
                    selected = filled
                }
                .clickable(role = Role.Button) {
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
                modifier = Modifier.size(46.dp),
                alpha = if (filled) 1f else 0.5f,
                colorFilter = if (filled) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            )
        }
    }
}
