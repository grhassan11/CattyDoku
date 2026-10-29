package com.threetech.catpuzzle.ui.screens.tutorial

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.tutorial.TutorialFocus
import com.threetech.catpuzzle.ui.components.CatIcon
import com.threetech.catpuzzle.ui.components.FishLife
import com.threetech.catpuzzle.ui.components.HintBulb
import com.threetech.catpuzzle.ui.components.PillButton
import com.threetech.catpuzzle.ui.screens.game.BoardLayoutCalc
import com.threetech.catpuzzle.ui.screens.game.BoardMetrics
import com.threetech.catpuzzle.ui.screens.game.BoardView
import com.threetech.catpuzzle.ui.screens.game.BOOSTER_BUTTON_DP
import com.threetech.catpuzzle.ui.screens.game.BOOSTER_ICON_DP
import com.threetech.catpuzzle.ui.screens.game.BOOSTER_SLOT_DP
import com.threetech.catpuzzle.ui.screens.game.BoosterButton
import com.threetech.catpuzzle.ui.theme.GameColors
import kotlin.math.min
import kotlin.math.roundToInt

private const val DIM_ALPHA = 0.5f
private const val FINGER_DP = 56f
private const val CTA_HEIGHT_DP = 62f
private const val CTA_GAP_DP = 8f
private const val CTA_RESERVED_DP = CTA_HEIGHT_DP + CTA_GAP_DP

/** Where the fingertip sits inside the icon (fraction of its size). Icon art is not documented: eyeballed. */
private const val FINGER_TIP_X = 0.3f
private const val FINGER_TIP_Y = 0.3f

/** Stateless tutorial screen: state in, lambdas out. */
@Composable
fun TutorialScreen(
    state: TutorialUiState,
    onCellTap: (Pos) -> Unit,
    onCellDoubleTap: (Pos) -> Unit,
    onDrag: (List<Pos>) -> Unit,
    onHint: () -> Unit,
    onCatBooster: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(GameColors.ScreenBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        TopRow(state, onSkip)
        Spacer(Modifier.height(8.dp))
        InstructionCard(state)
        Spacer(Modifier.height(8.dp))
        LivesRow(state)

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val n = state.board.size
            // CTA slot (gap + fixed height) sits directly under the board: reserve it from the board's budget.
            val metrics = BoardLayoutCalc.metrics(min(maxWidth.value, maxHeight.value - CTA_RESERVED_DP), n)
            val sideDp = metrics.gridSideDp(n) + 2 * metrics.cardPaddingDp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(sideDp.dp)) {
                    BoardView(
                        board = state.board,
                        patternMode = state.patternMode,
                        metrics = metrics,
                        enabled = true,
                        onTap = onCellTap,
                        onDoubleTap = onCellDoubleTap,
                        onDrag = onDrag,
                    )
                    BoardOverlay(state, metrics, Modifier.matchParentSize())
                }
                Spacer(Modifier.height(CTA_GAP_DP.dp))
                // Fixed-height slot: board does not jump when the CTA appears.
                Box(Modifier.width(sideDp.dp).height(CTA_HEIGHT_DP.dp), contentAlignment = Alignment.Center) {
                    if (state.showContinue) {
                        PillButton(
                            text = stringResource(if (state.continueIsFinal) R.string.tutorial_lets_play else R.string.tutorial_got_it),
                            onClick = onContinue,
                            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        BoostersRow(state, onCatBooster, onHint)
        Spacer(Modifier.height(16.dp))
    }
}

// ------------------------------------------------------------------ top

@Composable
private fun TopRow(state: TutorialUiState, onSkip: () -> Unit) {
    val progressText = stringResource(R.string.tutorial_progress, state.stepIndex + 1, state.stepCount)
    val skipText = stringResource(R.string.tutorial_skip)
    val skipDescription = stringResource(R.string.tutorial_skip_description)
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
                .background(GameColors.Disabled)
                .semantics { contentDescription = progressText },
        ) {
            Box(
                Modifier
                    .fillMaxWidth((state.stepIndex + 1f) / state.stepCount)
                    .fillMaxHeight()
                    .background(GameColors.Primary),
            )
        }
        Spacer(Modifier.size(12.dp))
        Box(
            Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSkip)
                .semantics { contentDescription = skipDescription }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(skipText, color = GameColors.Body, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InstructionCard(state: TutorialUiState) {
    val raw = stringResource(state.text.res())
    val accent = GameColors.Coral
    val annotated = remember(raw) {
        buildAnnotatedString {
            for (seg in AccentText.split(raw)) {
                if (seg.accent) {
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(seg.text) }
                } else {
                    append(seg.text)
                }
            }
        }
    }
    val notice = state.notice?.let { stringResource(it.res()) }

    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.rejectCount) {
        if (state.rejectCount > 0) {
            for (v in floatArrayOf(-10f, 10f, -6f, 6f, 0f)) shake.animateTo(v, tween(45))
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .graphicsLayer { translationX = shake.value * density }
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .background(GameColors.Card, RoundedCornerShape(20.dp))
            .heightIn(min = 120.dp)
            .padding(16.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = annotated,
            color = GameColors.Body,
            fontSize = 18.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        // Always one line tall so the card does not jump when the notice appears.
        Text(
            text = notice ?: " ",
            color = GameColors.Coral,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LivesRow(state: TutorialUiState) {
    val description = stringResource(R.string.game_lives, state.hearts, state.maxHearts)
    Row(
        Modifier
            .alpha(dimAlpha(state.focus, TutorialFocus.HEARTS))
            .background(GameColors.Card, CircleShape)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (i in 0 until state.maxHearts) FishLife(active = i < state.hearts, size = 28.dp)
    }
}

private fun dimAlpha(focus: TutorialFocus, mine: TutorialFocus): Float = if (focus == mine) 1f else DIM_ALPHA

// ------------------------------------------------------------------ board overlay

/** Draw-only layer over the board: no pointer handling, so gestures reach [BoardView]. */
@Composable
private fun BoardOverlay(state: TutorialUiState, metrics: BoardMetrics, modifier: Modifier = Modifier) {
    val n = state.board.size
    val dimAll = state.focus != TutorialFocus.BOARD
    val dimColor = Color(0xFF101010).copy(alpha = 0.55f)
    Canvas(modifier) {
        if (!dimAll && state.spotlight.isEmpty()) return@Canvas
        val pad = metrics.cardPaddingDp.dp.toPx()
        val gap = metrics.gapDp.dp.toPx()
        val cell = metrics.cellDp.dp.toPx()
        val radius = CornerRadius(8.dp.toPx())
        for (r in 0 until n) {
            for (c in 0 until n) {
                if (!dimAll && (r * n + c) in state.spotlight) continue
                drawRoundRect(
                    color = dimColor,
                    topLeft = Offset(pad + c * (cell + gap), pad + r * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = radius,
                )
            }
        }
    }

    val pointer = state.pointer
    val from = pointer?.from
    val to = pointer?.to
    if (!dimAll && pointer != null && from != null && to != null) {
        val progress = rememberLoopProgress()
        val fromC = cellCenterDp(from, metrics)
        val toC = cellCenterDp(to, metrics)
        FingerImage(
            positionDp = {
                val k = PointerMotion.travel(pointer.kind, progress.value)
                Offset(fromC.x + (toC.x - fromC.x) * k, fromC.y + (toC.y - fromC.y) * k)
            },
            scale = { PointerMotion.pressScale(pointer.kind, progress.value) },
        )
    }
}

private fun cellCenterDp(pos: Pos, m: BoardMetrics): Offset = Offset(
    m.cardPaddingDp + pos.col * (m.cellDp + m.gapDp) + m.cellDp / 2f,
    m.cardPaddingDp + pos.row * (m.cellDp + m.gapDp) + m.cellDp / 2f,
)

@Composable
private fun rememberLoopProgress(): State<Float> {
    val transition = rememberInfiniteTransition(label = "tutorialPointer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "tutorialPointerT",
    )
}

/**
 * Hand pointer ([R.drawable.ic_finger_tap]). [positionDp] / [scale] are lambdas so the per-frame
 * values are read in layout / draw phase, not in composition. Decorative: no content description.
 */
@Composable
private fun FingerImage(
    positionDp: () -> Offset,
    scale: () -> Float,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.ic_finger_tap),
        contentDescription = null,
        modifier = modifier
            .offset {
                val p = positionDp()
                IntOffset(
                    ((p.x - FINGER_DP * FINGER_TIP_X).dp.toPx()).roundToInt(),
                    ((p.y - FINGER_DP * FINGER_TIP_Y).dp.toPx()).roundToInt(),
                )
            }
            .size(FINGER_DP.dp)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(FINGER_TIP_X, FINGER_TIP_Y)
            },
    )
}

// ------------------------------------------------------------------ boosters

@Composable
private fun BoostersRow(state: TutorialUiState, onCatBooster: () -> Unit, onHint: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.alpha(dimAlpha(state.focus, TutorialFocus.CAT_BUTTON))) {
            BoosterButton(
                description = stringResource(R.string.booster_cat, state.catCharges),
                count = state.catCharges,
                onClick = onCatBooster,
            ) { CatIcon(size = BOOSTER_ICON_DP.dp) }
            if (state.focus == TutorialFocus.CAT_BUTTON) ButtonFinger(state)
        }
        Box(Modifier.alpha(dimAlpha(state.focus, TutorialFocus.HINT_BUTTON))) {
            BoosterButton(
                description = stringResource(R.string.booster_hint, state.hintCharges),
                count = state.hintCharges,
                onClick = onHint,
            ) { HintBulb(size = BOOSTER_ICON_DP.dp) }
            if (state.focus == TutorialFocus.HINT_BUTTON) ButtonFinger(state)
        }
    }
}

/** Finger over a booster button: circle sits at bottom-start above the shadow room; centre from the shared constants. */
@Composable
private fun ButtonFinger(state: TutorialUiState) {
    val pointer = state.pointer ?: return
    val progress = rememberLoopProgress()
    FingerImage(
        positionDp = { Offset(BOOSTER_BUTTON_DP / 2f, BOOSTER_SLOT_DP - BOOSTER_BUTTON_DP / 2f) },
        scale = { PointerMotion.pressScale(pointer.kind, progress.value) },
    )
}
