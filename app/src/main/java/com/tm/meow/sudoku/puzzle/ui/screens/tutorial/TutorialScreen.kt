package com.tm.meow.sudoku.puzzle.ui.screens.tutorial

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.model.Pos
import com.tm.meow.sudoku.puzzle.ui.screens.game.BoardGeo
import com.tm.meow.sudoku.puzzle.ui.screens.game.BoardLayoutCalc
import com.tm.meow.sudoku.puzzle.ui.screens.game.BoardMetrics
import com.tm.meow.sudoku.puzzle.ui.screens.game.BoardView
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors
import kotlin.math.min
import kotlin.math.roundToInt

private const val FINGER_DP = 56f

/**
 * Room kept above / below the board for the instruction card and the bottom block (second card, hint
 * pill, CTA, Skip / Replay links). Both sit in the overlay layer, so the board budget must leave it free.
 */
private const val TOP_RESERVE_DP = 150f
private const val BOTTOM_RESERVE_DP = 190f

/** Where the fingertip sits inside the icon (fraction of its size). Icon art is not documented: eyeballed. */
private const val FINGER_TIP_X = 0.3f
private const val FINGER_TIP_Y = 0.3f

/** Root-space bounds of the board, written on layout, read in draw / layout phases only. */
@Stable
internal class TutorialTargets {
    var board by mutableStateOf<BoardGeo?>(null)
}

/**
 * Stateless tutorial screen: state in, lambdas out. The base layer is the board only (on a bright
 * background); the [TutorialSpotlightOverlay] on top dims it on the dim steps (cells that hold a mark or
 * are ringed stay bright) and carries the instruction card, the bottom block and the confetti.
 * Cells that are not [TutorialUiState.interactive] sit under a touch gate.
 */
@Composable
fun TutorialScreen(
    state: TutorialUiState,
    onCellTap: (Pos) -> Unit,
    onCellDoubleTap: (Pos) -> Unit,
    onDrag: (List<Pos>) -> Unit,
    onHintPill: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targets = remember { TutorialTargets() }
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(GameColors.ScreenBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = TOP_RESERVE_DP.dp, bottom = BOTTOM_RESERVE_DP.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val n = state.board.size
                val metrics = BoardLayoutCalc.metrics(min(maxWidth.value, maxHeight.value), n)
                val sideDp = metrics.gridSideDp(n) + 2 * metrics.cardPaddingDp
                Box(
                    Modifier
                        .size(sideDp.dp)
                        .onGloballyPositioned { targets.board = BoardGeo(it.boundsInRoot(), metrics) },
                ) {
                    BoardView(
                        board = state.board,
                        patternMode = state.patternMode,
                        metrics = metrics,
                        enabled = true,
                        onTap = onCellTap,
                        onDoubleTap = onCellDoubleTap,
                        onDrag = onDrag,
                    )
                    BoardGate(state = state, metrics = metrics)
                }
            }
        }

        TutorialSpotlightOverlay(
            state = state,
            targets = targets,
            onHintPill = onHintPill,
            onContinue = onContinue,
            onSkip = onSkip,
            onReplay = onReplay,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Invisible touch sinks over every cell that is not interactive on this step, so only the highlighted
 * cells react. Sizes follow the board's even hit grid (grid side / size). A drag that already started
 * on an interactive cell keeps its pointer when it leaves it.
 */
@Composable
private fun BoardGate(state: TutorialUiState, metrics: BoardMetrics) {
    val n = state.board.size
    val pitch = metrics.gridSideDp(n) / n
    val gated = remember(n, state.interactive) { SpotlightLogic.gatedCells(n, state.interactive) }
    for (i in gated) {
        Box(
            Modifier
                .offset(
                    x = (metrics.cardPaddingDp + (i % n) * pitch).dp,
                    y = (metrics.cardPaddingDp + (i / n) * pitch).dp,
                )
                .size(pitch.dp)
                .pointerInput(Unit) { detectTapGestures() },
        )
    }
}

// ------------------------------------------------------------------ finger

internal fun cellCenterDp(pos: Pos, m: BoardMetrics): Offset = Offset(
    m.cardPaddingDp + pos.col * (m.cellDp + m.gapDp) + m.cellDp / 2f,
    m.cardPaddingDp + pos.row * (m.cellDp + m.gapDp) + m.cellDp / 2f,
)

@Composable
internal fun rememberLoopProgress(): State<Float> {
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
 * [positionDp] is relative to the parent's top-left.
 */
@Composable
internal fun FingerImage(
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
