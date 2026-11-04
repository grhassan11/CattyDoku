package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.BoxShape
import com.tm.meow.sudoku.puzzle.ui.screens.game.rememberAnimationsEnabled
import com.tm.meow.sudoku.puzzle.ui.theme.FredokaFamily
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors

/** Neutral board colours, derived from [GameColors]. Highlight tints come from [SudokuTierTheme]. */
internal object SudokuColors {
    val Board = GameColors.Card
    val PlayerDigit = GameColors.CtaTealLip
    val WrongDigit = GameColors.Conflict
}

private val BoardShape = RoundedCornerShape(14.dp)

/**
 * Square N x N board. Cells are a plain Column / Row grid (each cell is its own a11y node); one overlay pass draws the thin
 * cell lines and the thick box lines (box = [boxRows] x [boxCols] cells, so 6x6 gets 2-row x 3-col boxes).
 * Sizes derive from [size]: digit text uses Dp.toSp so it does not grow with the system font scale.
 */
@Composable
internal fun SudokuBoard(
    cells: List<SudokuCellUi>,
    n: Int,
    boxRows: Int,
    boxCols: Int,
    selected: Int,
    inputEnabled: Boolean,
    onCellTap: (Int) -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    if (cells.size != n * n) return
    val cell = size / n
    val digitSp = with(LocalDensity.current) { (cell * 0.58f).toSp() }
    val noteSp = with(LocalDensity.current) { (cell * (if (n >= 8) 0.26f else 0.3f)).toSp() }
    val box = remember(boxRows, boxCols) { BoxShape(boxRows, boxCols) }
    val theme = LocalSudokuTierTheme.current

    // Effects: compare each new cell list with the previous one (plain holder, no state read). Off when animations are disabled.
    val animationsOn = rememberAnimationsEnabled(null)
    val previous = remember { PreviousCells() }
    var fx by remember { mutableStateOf(SudokuFxEvent.NONE) }
    LaunchedEffect(cells) {
        val before = previous.cells
        previous.cells = cells
        if (animationsOn && before != null) {
            SudokuBoardFx.detect(before, cells, n, boxRows, boxCols, fx.id + 1)?.let { fx = it }
        }
    }
    val shakePx = with(LocalDensity.current) { 5.dp.toPx() }

    Box(
        modifier
            .size(size)
            .clip(BoardShape)
            .background(SudokuColors.Board)
            .drawWithContent {
                drawContent()
                val px = this.size.width / n
                val thin = 1.dp.toPx()
                val thick = 2.5.dp.toPx()
                for (i in 1 until n) {
                    val onBox = i % boxCols == 0
                    drawLine(
                        if (onBox) theme.ink else theme.line,
                        Offset(i * px, 0f), Offset(i * px, this.size.height),
                        strokeWidth = if (onBox) thick else thin,
                    )
                }
                for (i in 1 until n) {
                    val onBox = i % boxRows == 0
                    drawLine(
                        if (onBox) theme.ink else theme.line,
                        Offset(0f, i * px), Offset(this.size.width, i * px),
                        strokeWidth = if (onBox) thick else thin,
                    )
                }
            }
            .border(2.5.dp, theme.ink, BoardShape),
    ) {
        Column(Modifier.fillMaxSize()) {
            for (r in 0 until n) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (c in 0 until n) {
                        val index = r * n + c
                        BoardCell(
                            ui = cells[index],
                            row = r,
                            col = c,
                            isSelected = index == selected,
                            enabled = inputEnabled,
                            box = box,
                            theme = theme,
                            digitSp = digitSp,
                            noteSp = noteSp,
                            pulseOnSelect = animationsOn,
                            popToken = if (fx.popCell == index) fx.id else 0,
                            shakeToken = if (fx.shakeCell == index) fx.id else 0,
                            flashToken = if (index in fx.flashCells) fx.id else 0,
                            shakePx = shakePx,
                            onClick = { onCellTap(index) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardCell(
    ui: SudokuCellUi,
    row: Int,
    col: Int,
    isSelected: Boolean,
    enabled: Boolean,
    box: BoxShape,
    theme: SudokuTierTheme,
    digitSp: androidx.compose.ui.unit.TextUnit,
    noteSp: androidx.compose.ui.unit.TextUnit,
    pulseOnSelect: Boolean,
    popToken: Int,
    shakeToken: Int,
    flashToken: Int,
    shakePx: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cellFx = if (popToken != 0 || shakeToken != 0 || flashToken != 0) {
        rememberCellFx(popToken, shakeToken, flashToken, shakePx)
    } else {
        null
    }
    val fill = when (ui.highlight) {
        CellHighlight.SELECTED -> theme.selectedFill
        CellHighlight.SAME_DIGIT -> theme.sameDigitFill
        CellHighlight.PEER -> theme.peerFill
        CellHighlight.NONE -> Color.Transparent
    }
    val description = cellDescription(ui, row + 1, col + 1)
    Box(
        modifier
            .fillMaxSize()
            .background(fill)
            .drawBehind { cellFx?.let { drawRect(theme.unitFlash.copy(alpha = it.flash.value)) } }
            .clickable(enabled = enabled, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) Box(Modifier.fillMaxSize().border(2.dp, theme.selectRing))
        if (isSelected && pulseOnSelect) SelectPulse(theme.selectRing)
        when {
            ui.value != 0 -> Text(
                ui.value.toString(),
                modifier = if (cellFx == null) {
                    Modifier
                } else {
                    Modifier.graphicsLayer {
                        scaleX = cellFx.pop.value
                        scaleY = cellFx.pop.value
                        translationX = cellFx.shake.value
                    }
                },
                color = when {
                    ui.wrong -> SudokuColors.WrongDigit
                    ui.given -> theme.ink
                    else -> SudokuColors.PlayerDigit
                },
                fontSize = digitSp,
                fontWeight = if (ui.given) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = FredokaFamily,
                maxLines = 1,
                softWrap = false,
            )
            ui.notes != 0 -> NotesGrid(ui.notes, box, noteSp, theme.inkSoft.copy(alpha = 0.85f))
        }
    }
}

private class PreviousCells {
    var cells: List<SudokuCellUi>? = null
}

/** Per-cell effect values. Read only in draw / layer lambdas, so animating them never recomposes the board. */
private class CellFx(
    val pop: Animatable<Float, AnimationVector1D>,
    val shake: Animatable<Float, AnimationVector1D>,
    val flash: Animatable<Float, AnimationVector1D>,
)

private val ShakeSteps = floatArrayOf(-1f, 1f, -0.7f, 0.7f, 0f)

/** Created only for the few cells an event touches, never for all of them. */
@Composable
private fun rememberCellFx(popToken: Int, shakeToken: Int, flashToken: Int, shakePx: Float): CellFx {
    val fx = remember { CellFx(Animatable(1f), Animatable(0f), Animatable(0f)) }
    LaunchedEffect(popToken) {
        if (popToken != 0) {
            fx.pop.snapTo(0.55f)
            fx.pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    LaunchedEffect(shakeToken) {
        if (shakeToken != 0) for (step in ShakeSteps) fx.shake.animateTo(step * shakePx, tween(45))
    }
    LaunchedEffect(flashToken) {
        if (flashToken != 0) {
            fx.flash.snapTo(0.5f)
            fx.flash.animateTo(0f, tween(650))
        }
    }
    return fx
}

/** Short ring that grows to the cell edge when the cell becomes selected. Exists only while the cell is selected. */
@Composable
private fun SelectPulse(ring: Color) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(220)) }
    Box(
        Modifier.fillMaxSize().drawBehind {
            val t = progress.value
            if (t >= 1f) return@drawBehind
            val inset = (1f - t) * size.minDimension * 0.3f
            drawRect(
                ring.copy(alpha = (1f - t) * 0.8f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - 2 * inset, size.height - 2 * inset),
                style = Stroke(2.dp.toPx()),
            )
        },
    )
}

/** Pencil marks: digit d sits at slot ((d - 1) / box.cols, (d - 1) % box.cols) of a box.rows x box.cols grid. */
@Composable
private fun NotesGrid(mask: Int, box: BoxShape, noteSp: androidx.compose.ui.unit.TextUnit, noteColor: Color) {
    Column(Modifier.fillMaxSize().padding(1.dp)) {
        for (r in 0 until box.rows) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (c in 0 until box.cols) {
                    val digit = r * box.cols + c + 1
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (mask and (1 shl (digit - 1)) != 0) {
                            Text(
                                digit.toString(),
                                color = noteColor,
                                fontSize = noteSp,
                                fontFamily = FredokaFamily,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun cellDescription(ui: SudokuCellUi, row: Int, col: Int): String {
    val state = when {
        ui.value != 0 && ui.wrong -> stringResource(R.string.sudoku_cell_wrong, ui.value)
        ui.value != 0 && ui.given -> stringResource(R.string.sudoku_cell_given, ui.value)
        ui.value != 0 -> stringResource(R.string.sudoku_cell_value, ui.value)
        ui.notes != 0 -> stringResource(R.string.sudoku_cell_notes, SudokuNotesText.list(ui.notes))
        else -> stringResource(R.string.sudoku_cell_empty)
    }
    return stringResource(R.string.sudoku_cell_description, row, col, state)
}

internal object SudokuNotesText {
    fun list(mask: Int): String = (1..9).filter { mask and (1 shl (it - 1)) != 0 }.joinToString(", ")
}
