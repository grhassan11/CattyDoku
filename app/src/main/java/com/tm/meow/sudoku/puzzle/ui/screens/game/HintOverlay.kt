package com.tm.meow.sudoku.puzzle.ui.screens.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.model.PieceSkin
import com.tm.meow.sudoku.puzzle.ui.components.HintBulb
import com.tm.meow.sudoku.puzzle.ui.components.PillButton
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors

private const val HINT_PULSE_MS = 800

/** Board position (root space) + the metrics it was laid out with, for the spotlight cut-outs. */
internal data class BoardGeo(val bounds: Rect, val metrics: BoardMetrics)

/**
 * Hint dim: a screen-wide scrim with the [HintOverlayUi.spotlight] cells cut out, plus a rule card at the top
 * (over the rules strip area, clear of the board). Draws ABOVE the screen, so board geometry is untouched and
 * everything underneath (boosters, buttons, board) is dimmed and inert. Tap anywhere, "Got it" or system back dismisses.
 *
 * Holes come from [boardGeo] (board root-space bounds + metrics, written by the board's onGloballyPositioned);
 * read in the draw phase only, so layout changes never recompose this.
 */
@Composable
internal fun HintOverlay(
    overlay: HintOverlayUi,
    boardGeo: State<BoardGeo?>,
    boardSize: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    pieceSkin: PieceSkin = PieceSkin.cats(),
    /** Flat indices of the hint's target cell(s): their stroke breathes (glow pulse) while the overlay is up. */
    targets: Set<Int> = emptySet(),
) {
    BackHandler(onBack = onDismiss)
    // Breathing phase 0..1..0. The infinite transition exists ONLY while this overlay is composed (= visible); none when animations are off.
    val pulse: State<Float>? = if (rememberAnimationsEnabled(null) && targets.isNotEmpty()) {
        rememberInfiniteTransition(label = "hintPulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(HINT_PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "hintPulsePhase",
        )
    } else {
        null
    }
    val dismissLabel = stringResource(R.string.hint_overlay_dismiss)
    val origin = remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = dismissLabel,
                role = Role.Button,
                onClick = onDismiss,
            ),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                // Offscreen so BlendMode.Clear punches real holes instead of painting black.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .onGloballyPositioned { origin.value = it.positionInRoot() },
        ) {
            drawRect(GameColors.HintDim)
            val geo = boardGeo.value ?: return@Canvas
            val b = geo.bounds
            val metrics = geo.metrics
            val pad = metrics.cardPaddingDp.dp.toPx()
            val cell = metrics.cellDp.dp.toPx()
            val gap = metrics.gapDp.dp.toPx()
            val pitch = cell + gap
            val grow = gap / 2f
            val radius = BoardLayoutCalc.cellCornerPx(cell, 1.dp.toPx()) + grow
            val left = b.left - origin.value.x + pad
            val top = b.top - origin.value.y + pad
            for (i in overlay.spotlight) {
                val r = i / boardSize
                val c = i % boardSize
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(left + c * pitch - grow, top + r * pitch - grow),
                    size = Size(cell + 2 * grow, cell + 2 * grow),
                    cornerRadius = CornerRadius(radius),
                    blendMode = BlendMode.Clear,
                )
            }
            // Target cell: breathing gold stroke + an expanding soft halo (static stroke when animations are off).
            val phase = pulse?.value ?: 0.5f
            for (i in targets) {
                val r = i / boardSize
                val c = i % boardSize
                val tl = Offset(left + c * pitch, top + r * pitch)
                val w = (3f + 3f * phase).dp.toPx()
                drawRoundRect(
                    color = GameColors.HintTarget.copy(alpha = 0.55f + 0.45f * phase),
                    topLeft = Offset(tl.x + w / 2f, tl.y + w / 2f),
                    size = Size(cell - w, cell - w),
                    cornerRadius = CornerRadius((radius - w / 2f).coerceAtLeast(0f)),
                    style = Stroke(w),
                )
                if (pulse != null) {
                    val g = (2f + 6f * phase).dp.toPx()
                    drawRoundRect(
                        color = GameColors.HintTarget.copy(alpha = 0.45f * (1f - phase)),
                        topLeft = Offset(tl.x - g, tl.y - g),
                        size = Size(cell + 2f * g, cell + 2f * g),
                        cornerRadius = CornerRadius(radius + g),
                        style = Stroke(3.dp.toPx()),
                    )
                }
            }
        }
        HintRuleCard(
            hint = overlay.hint,
            word = pieceSkin.word(),
            onDismiss = onDismiss,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
    }
}

@Composable
private fun HintRuleCard(hint: HintUi, word: PieceWord, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val ruleText = hint.rule?.let { stringResource(PieceWording.rule(it, word)) }
    val message = stringResource(PieceWording.message(hint.message, word))
    val spoken = stringResource(R.string.hint_overlay_description, listOfNotNull(ruleText, message).joinToString(" "))
    Column(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .background(GameColors.Card, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = spoken
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HintBulb(size = 32.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (ruleText != null) {
                    Text(ruleText, color = GameColors.Title, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                }
                Text(message, color = GameColors.Body, fontSize = 14.sp, lineHeight = 18.sp)
            }
        }
        PillButton(
            text = stringResource(R.string.hint_overlay_got_it),
            onClick = onDismiss,
            textSize = 18.sp,
        )
    }
}
