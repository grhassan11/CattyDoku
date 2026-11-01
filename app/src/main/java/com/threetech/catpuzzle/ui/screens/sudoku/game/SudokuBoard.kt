package com.threetech.catpuzzle.ui.screens.sudoku.game

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.sudoku.model.BoxShape
import com.threetech.catpuzzle.ui.theme.FredokaFamily
import com.threetech.catpuzzle.ui.theme.GameColors

/** Board colours, all derived from [GameColors] (no new palette). */
internal object SudokuColors {
    val Board = GameColors.Card
    val PeerFill = GameColors.CtaCreamLip.copy(alpha = 0.45f)
    val SameDigitFill = GameColors.Primary.copy(alpha = 0.22f)
    val SelectedFill = GameColors.Primary.copy(alpha = 0.5f)
    val ThinLine = GameColors.CtaCreamLip
    val ThickLine = GameColors.Title
    val GivenDigit = GameColors.Title
    val PlayerDigit = GameColors.CtaTealLip
    val WrongDigit = GameColors.Conflict
    val NoteDigit = GameColors.Body.copy(alpha = 0.75f)
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
    val noteSp = with(LocalDensity.current) { (cell * (if (n == 9) 0.26f else 0.3f)).toSp() }
    val box = remember(boxRows, boxCols) { BoxShape(boxRows, boxCols) }
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
                        if (onBox) SudokuColors.ThickLine else SudokuColors.ThinLine,
                        Offset(i * px, 0f), Offset(i * px, this.size.height),
                        strokeWidth = if (onBox) thick else thin,
                    )
                }
                for (i in 1 until n) {
                    val onBox = i % boxRows == 0
                    drawLine(
                        if (onBox) SudokuColors.ThickLine else SudokuColors.ThinLine,
                        Offset(0f, i * px), Offset(this.size.width, i * px),
                        strokeWidth = if (onBox) thick else thin,
                    )
                }
            }
            .border(2.5.dp, SudokuColors.ThickLine, BoardShape),
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
                            digitSp = digitSp,
                            noteSp = noteSp,
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
    digitSp: androidx.compose.ui.unit.TextUnit,
    noteSp: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = when (ui.highlight) {
        CellHighlight.SELECTED -> SudokuColors.SelectedFill
        CellHighlight.SAME_DIGIT -> SudokuColors.SameDigitFill
        CellHighlight.PEER -> SudokuColors.PeerFill
        CellHighlight.NONE -> Color.Transparent
    }
    val description = cellDescription(ui, row + 1, col + 1)
    Box(
        modifier
            .fillMaxSize()
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                selected = isSelected
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            ui.value != 0 -> Text(
                ui.value.toString(),
                color = when {
                    ui.wrong -> SudokuColors.WrongDigit
                    ui.given -> SudokuColors.GivenDigit
                    else -> SudokuColors.PlayerDigit
                },
                fontSize = digitSp,
                fontWeight = if (ui.given) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = FredokaFamily,
                maxLines = 1,
                softWrap = false,
            )
            ui.notes != 0 -> NotesGrid(ui.notes, box, noteSp)
        }
    }
}

/** Pencil marks: digit d sits at slot ((d - 1) / box.cols, (d - 1) % box.cols) of a box.rows x box.cols grid. */
@Composable
private fun NotesGrid(mask: Int, box: BoxShape, noteSp: androidx.compose.ui.unit.TextUnit) {
    Column(Modifier.fillMaxSize().padding(1.dp)) {
        for (r in 0 until box.rows) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (c in 0 until box.cols) {
                    val digit = r * box.cols + c + 1
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (mask and (1 shl (digit - 1)) != 0) {
                            Text(
                                digit.toString(),
                                color = SudokuColors.NoteDigit,
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
