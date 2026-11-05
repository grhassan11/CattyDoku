package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.sudoku.tutorial.SudokuTutorialPhase
import com.tm.meow.sudoku.puzzle.ui.components.ButtonStyle
import com.tm.meow.sudoku.puzzle.ui.components.CatIcon
import com.tm.meow.sudoku.puzzle.ui.components.DownsampledIconImage
import com.tm.meow.sudoku.puzzle.ui.components.IconArt
import com.tm.meow.sudoku.puzzle.ui.components.IconImage
import com.tm.meow.sudoku.puzzle.ui.components.PillButton
import com.tm.meow.sudoku.puzzle.ui.components.homePattern
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game.CellHighlight
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game.CircleButton
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game.DialogBody
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game.DialogCard
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game.DialogTitle
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game.SudokuBoard
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game.SudokuCellUi
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game.LocalSudokuTierTheme
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game.SudokuColors
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game.sudokuTierTheme
import com.tm.meow.sudoku.puzzle.ui.theme.FredokaFamily
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors
import kotlinx.coroutines.delay

private val ContentMaxWidth = 480.dp
private const val DIM_ALPHA = 0.74f
private const val MARK_FILL_ALPHA = 0.16f
private val BoardShape = RoundedCornerShape(14.dp)
private val FingerSize = 56.dp

/**
 * Sudoku tutorial screen (2 lessons, one fixed board per size: 4x4, 6x6, 8x8, 9x9). Stateless: state + lambdas only
 * ([SudokuTutorialRoute] owns the VM). Looks like gameplay: it provides the gameplay theme (colours + ink) of the lowest
 * tier that plays on the size ([SudokuTutorialUiState.themeTier]). Reuses [SudokuBoard], the game dialog card and
 * [PillButton]; adds a dim / x-mark overlay and a hand cue on top.
 * [onExit] = Skip, the top-left back and system back (the flag is saved either way).
 */
@Composable
fun SudokuTutorialScreen(
    state: SudokuTutorialUiState,
    onExit: () -> Unit,
    onStartLesson: () -> Unit,
    onCellTap: (Int) -> Unit,
    onDigit: (Int) -> Unit,
    onNextLesson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onExit)
    var toast by remember { mutableStateOf(false) }
    LaunchedEffect(state.solved) {
        if (state.solved > 0) {
            toast = true
            delay(1100)
            toast = false
        }
    }

    val theme = remember(state.themeTier) { sudokuTierTheme(state.themeTier) }
    CompositionLocalProvider(LocalSudokuTierTheme provides theme) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(theme.backgroundTop, theme.backgroundBottom)))
            .homePattern(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .widthIn(max = ContentMaxWidth)
                .systemBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopRow(state.lesson, onExit)
            Spacer(Modifier.height(12.dp))
            InstructionCard(state)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side: Dp = minOf(maxWidth, maxHeight)
                TutorialBoard(state, side, onCellTap)
                if (toast) SuccessPill(Modifier.align(Alignment.TopCenter))
            }
            Spacer(Modifier.height(8.dp))
            TutorialPad(state, onDigit)
        }

        when (state.phase) {
            SudokuTutorialPhase.INTRO -> IntroDialog(state.lesson, onStart = onStartLesson, onSkip = onExit)
            SudokuTutorialPhase.LESSON_COMPLETE -> CompleteDialog(state.lesson, state.size.n, onNext = onNextLesson, onFinish = onExit)
            SudokuTutorialPhase.TAP_CELL, SudokuTutorialPhase.ENTER_DIGIT -> Unit
        }
    }
    }
}

// ------------------------------------------------------------------ frame

@Composable
private fun TopRow(lesson: Int, onExit: () -> Unit) {
    val theme = LocalSudokuTierTheme.current
    val name = stringResource(if (lesson == 1) R.string.sudoku_tut_lesson_1_name else R.string.sudoku_tut_lesson_2_name)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(stringResource(R.string.sudoku_back), onExit) {
            IconImage(R.drawable.ic_back, size = 22.dp, tint = theme.ink)
        }
        Text(
            stringResource(R.string.sudoku_tut_lesson_title, lesson, name),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
            color = theme.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FredokaFamily,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onExit)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.sudoku_tut_skip), color = theme.inkSoft, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InstructionCard(state: SudokuTutorialUiState) {
    val theme = LocalSudokuTierTheme.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(theme.accent.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
            .border(2.dp, theme.accent, RoundedCornerShape(20.dp))
            .heightIn(min = 124.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.segments.forEach { seg ->
                Box(
                    Modifier
                        .size(width = 30.dp, height = 6.dp)
                        .background(
                            when (seg) {
                                TutorialSegment.DONE -> theme.accentStrong
                                TutorialSegment.CURRENT -> theme.accent
                                TutorialSegment.TODO -> GameColors.Disabled
                            },
                            CircleShape,
                        ),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(state.textRes, *state.textArgs.toTypedArray()),
            color = theme.ink,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SuccessPill(modifier: Modifier = Modifier) {
    val theme = LocalSudokuTierTheme.current
    Row(
        modifier
            .padding(top = 8.dp)
            .shadow(2.dp, CircleShape)
            .background(theme.accentStrong, CircleShape)
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.sudoku_tut_success), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FredokaFamily)
        IconImage(R.drawable.ic_paw, size = 18.dp, tint = Color.White)
    }
}

// ------------------------------------------------------------------ board

@Composable
private fun TutorialBoard(state: SudokuTutorialUiState, side: Dp, onCellTap: (Int) -> Unit) {
    val n = state.size.n
    val theme = LocalSudokuTierTheme.current
    val cellDp = side / n
    val boardCells = remember(state.cells) {
        state.cells.map {
            SudokuCellUi(
                value = it.value,
                notes = 0,
                given = true,
                wrong = false,
                highlight = if (it.target) CellHighlight.SELECTED else CellHighlight.NONE,
            )
        }
    }
    Box(Modifier.size(side)) {
        SudokuBoard(
            cells = boardCells,
            n = n,
            boxRows = state.size.box.rows,
            boxCols = state.size.box.cols,
            selected = -1,
            inputEnabled = state.phase == SudokuTutorialPhase.TAP_CELL,
            onCellTap = onCellTap,
            size = side,
        )
        // Draw-only layer (no pointer handling): taps reach the board underneath.
        val dimColor = theme.ink.copy(alpha = DIM_ALPHA)
        val markFill = theme.unitFlash.copy(alpha = MARK_FILL_ALPHA)
        val markInk = theme.accentStrong
        Canvas(Modifier.matchParentSize().clip(BoardShape)) {
            val px = size.width / n
            val stroke = 2.dp.toPx()
            state.cells.forEachIndexed { i, c ->
                val topLeft = Offset((i % n) * px, (i / n) * px)
                if (c.mark) {
                    drawRect(markFill, topLeft, Size(px, px))
                    val inset = px * 0.3f
                    drawLine(markInk, topLeft + Offset(inset, inset), topLeft + Offset(px - inset, px - inset), stroke, StrokeCap.Round)
                    drawLine(markInk, topLeft + Offset(px - inset, inset), topLeft + Offset(inset, px - inset), stroke, StrokeCap.Round)
                }
                if (c.dim) drawRect(dimColor, topLeft, Size(px, px))
            }
        }
        val questionCell = state.cells.indexOfFirst { it.question }
        if (questionCell >= 0) {
            val digitSp = with(LocalDensity.current) { (cellDp * 0.58f).toSp() }
            Box(
                Modifier
                    .offset(x = cellDp * (questionCell % n), y = cellDp * (questionCell / n))
                    .size(cellDp),
                contentAlignment = Alignment.Center,
            ) {
                Text("?", color = theme.accentStrong, fontSize = digitSp, fontWeight = FontWeight.Bold, fontFamily = FredokaFamily, maxLines = 1)
            }
        }
        if (state.pointerCell >= 0) {
            val pulse = rememberPulse()
            Image(
                painter = painterResource(R.drawable.ic_finger_tap),
                contentDescription = null,
                modifier = Modifier
                    .offset(
                        x = cellDp * ((state.pointerCell % n) + 0.5f) - FingerSize * 0.3f,
                        y = cellDp * ((state.pointerCell / n) + 0.5f) - FingerSize * 0.3f,
                    )
                    .size(FingerSize)
                    .graphicsLayer {
                        scaleX = pulse.value
                        scaleY = pulse.value
                        transformOrigin = TransformOrigin(0.3f, 0.3f)
                    },
            )
        }
    }
}

@Composable
private fun rememberPulse(): androidx.compose.runtime.State<Float> {
    val transition = rememberInfiniteTransition(label = "sudokuTutorialPulse")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "sudokuTutorialPulseT",
    )
}

// ------------------------------------------------------------------ pad

/** Digits 1..n in the game pad style (tier pad fill + border); only the one the step asks for is enabled (highlighted, with the hand on it). */
@Composable
private fun TutorialPad(state: SudokuTutorialUiState, onDigit: (Int) -> Unit) {
    val theme = LocalSudokuTierTheme.current
    val n = state.size.n
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().widthIn(max = (n * 72).dp),
        horizontalArrangement = Arrangement.spacedBy(if (n > 6) 4.dp else 8.dp),
    ) {
        for (d in 1..n) {
            val enabled = state.enabledDigit == d
            Box(Modifier.weight(1f).height(60.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .alpha(if (enabled) 1f else 0.35f)
                        .shadow(1.dp, shape)
                        .clip(shape)
                        .background(theme.padFill)
                        .then(if (enabled) Modifier.background(theme.accent.copy(alpha = 0.3f)) else Modifier)
                        .then(
                            if (theme.padBorderWidthDp > 0f) {
                                Modifier.border(theme.padBorderWidthDp.dp, theme.padBorder, shape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable(enabled = enabled, role = Role.Button) { onDigit(d) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        d.toString(),
                        color = SudokuColors.PlayerDigit,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FredokaFamily,
                        maxLines = 1,
                    )
                }
                if (enabled) {
                    val pulse = rememberPulse()
                    Image(
                        painter = painterResource(R.drawable.ic_finger_tap),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = 6.dp, y = 22.dp)
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = pulse.value
                                scaleY = pulse.value
                                transformOrigin = TransformOrigin(0.3f, 0.3f)
                            },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
private fun IntroDialog(lesson: Int, onStart: () -> Unit, onSkip: () -> Unit) {
    val first = lesson == 1
    val title = stringResource(if (first) R.string.sudoku_tut_intro_title_1 else R.string.sudoku_tut_intro_title_2)
    DialogCard(title, onScrimTap = null) {
        CatIcon(size = 72.dp)
        Spacer(Modifier.height(10.dp))
        DialogTitle(title)
        Spacer(Modifier.height(6.dp))
        DialogBody(stringResource(if (first) R.string.sudoku_tut_intro_body_1 else R.string.sudoku_tut_intro_body_2))
        Spacer(Modifier.height(20.dp))
        PillButton(
            text = stringResource(R.string.sudoku_tut_start_lesson),
            onClick = onStart,
            style = ButtonStyle.Teal,
            textSize = 18.sp,
            horizontalPadding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        LinkText(stringResource(if (first) R.string.sudoku_tut_skip_all else R.string.sudoku_tut_skip_tricks), onSkip)
    }
}

@Composable
private fun CompleteDialog(lesson: Int, n: Int, onNext: () -> Unit, onFinish: () -> Unit) {
    val first = lesson == 1
    val title = stringResource(if (first) R.string.sudoku_tut_done_title_1 else R.string.sudoku_tut_done_title_2)
    DialogCard(title, onScrimTap = null) {
        DownsampledIconImage(IconArt.Trophy, size = 64.dp)
        Spacer(Modifier.height(10.dp))
        DialogTitle(title)
        Spacer(Modifier.height(8.dp))
        DialogBody(stringResource(if (first) R.string.sudoku_tut_done_body_1 else R.string.sudoku_tut_done_body_2, n))
        Spacer(Modifier.height(20.dp))
        if (first) {
            PillButton(
                text = stringResource(R.string.sudoku_tut_next_tricks),
                onClick = onNext,
                style = ButtonStyle.Teal,
                textSize = 18.sp,
                horizontalPadding = 16.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
        }
        PillButton(
            text = stringResource(R.string.sudoku_tut_start_playing),
            onClick = onFinish,
            textSize = 18.sp,
            horizontalPadding = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .width(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = LocalSudokuTierTheme.current.inkSoft,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline,
            textAlign = TextAlign.Center,
        )
    }
}
