package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.tutorial

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuSize
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier
import com.tm.meow.sudoku.puzzle.domain.sudoku.tutorial.SudokuTutorialKind
import com.tm.meow.sudoku.puzzle.domain.sudoku.tutorial.SudokuTutorialPhase
import com.tm.meow.sudoku.puzzle.domain.sudoku.tutorial.SudokuTutorialScript
import com.tm.meow.sudoku.puzzle.domain.sudoku.tutorial.SudokuTutorialState

enum class TutorialSegment { DONE, CURRENT, TODO }

/** [dim] = outside the spotlight; [mark] = drawn with an x (cannot hold the step digit); [target] = the cell to tap. */
@Immutable
data class SudokuTutorialCellUi(
    val value: Int,
    val dim: Boolean,
    val mark: Boolean,
    val target: Boolean,
    val question: Boolean,
)

@Immutable
data class SudokuTutorialUiState(
    val size: SudokuSize,
    /** Lowest tier on [size]: the screen uses its gameplay theme + ink. */
    val themeTier: SudokuTier,
    val lesson: Int,
    val phase: SudokuTutorialPhase,
    val cells: List<SudokuTutorialCellUi>,
    val segments: List<TutorialSegment>,
    @StringRes val textRes: Int,
    /** Format arguments of [textRes] (positional, see strings.xml). */
    val textArgs: List<Int>,
    /** Digit the instruction text talks about (format argument). */
    val digit: Int,
    /** Pad key that is enabled (0 = none). */
    val enabledDigit: Int,
    /** Cell the hand points at while a cell tap is expected (-1 = none). */
    val pointerCell: Int,
    /** Count of correct steps so far; changes trigger the success toast. */
    val solved: Int,
)

/** Pure state -> UI mapping for the Sudoku tutorial. */
object SudokuTutorialUiMapper {

    fun map(s: SudokuTutorialState): SudokuTutorialUiState {
        val active = s.phase == SudokuTutorialPhase.TAP_CELL || s.phase == SudokuTutorialPhase.ENTER_DIGIT
        val step = s.currentStep.takeIf { active }
        val spotlight = if (step != null) SudokuTutorialScript.spotlight(s.size, s.values, step) else emptySet()
        val marks = if (step != null) SudokuTutorialScript.marks(s.size, s.values, step) else emptySet()
        val cells = List(s.size.cells) { i ->
            SudokuTutorialCellUi(
                value = s.values[i],
                dim = step != null && i !in spotlight,
                mark = i in marks,
                target = step != null && i == step.cell,
                question = step != null && s.phase == SudokuTutorialPhase.ENTER_DIGIT && i == step.cell,
            )
        }
        val segments = List(s.stepCount) { i ->
            when {
                s.phase == SudokuTutorialPhase.LESSON_COMPLETE || i < s.step -> TutorialSegment.DONE
                i == s.step -> TutorialSegment.CURRENT
                else -> TutorialSegment.TODO
            }
        }
        return SudokuTutorialUiState(
            size = s.size,
            themeTier = SudokuTutorialScript.themeTier(s.size),
            lesson = s.lesson,
            phase = s.phase,
            cells = cells,
            segments = segments,
            textRes = textRes(s),
            textArgs = textArgs(s),
            digit = s.currentStep?.digit ?: 0,
            enabledDigit = if (step != null && s.phase == SudokuTutorialPhase.ENTER_DIGIT) step.digit else 0,
            pointerCell = if (step != null && s.phase == SudokuTutorialPhase.TAP_CELL) step.cell else -1,
            solved = s.solved,
        )
    }

    /** Positional format args for [textRes]: digit-only strings get the digit, rule strings get the size numbers they print. */
    fun textArgs(s: SudokuTutorialState): List<Int> {
        val step = s.currentStep
        val active = s.phase == SudokuTutorialPhase.TAP_CELL || s.phase == SudokuTutorialPhase.ENTER_DIGIT
        if (step == null || !active) return listOf(s.size.n)
        val tap = s.phase == SudokuTutorialPhase.TAP_CELL
        return when {
            tap && (step.kind == SudokuTutorialKind.ROW || step.kind == SudokuTutorialKind.COLUMN) -> listOf(s.size.n)
            tap && step.kind == SudokuTutorialKind.BLOCK -> listOf(s.size.box.rows, s.size.box.cols, s.size.n)
            else -> listOf(step.digit)
        }
    }

    @StringRes
    fun textRes(s: SudokuTutorialState): Int {
        val step = s.currentStep
        return when (s.phase) {
            SudokuTutorialPhase.INTRO ->
                if (s.lesson == 1) R.string.sudoku_tut_card_intro_1 else R.string.sudoku_tut_card_intro_2
            SudokuTutorialPhase.LESSON_COMPLETE ->
                if (s.lesson == 1) R.string.sudoku_tut_card_done_1 else R.string.sudoku_tut_card_done_2
            SudokuTutorialPhase.TAP_CELL, SudokuTutorialPhase.ENTER_DIGIT -> {
                val tap = s.phase == SudokuTutorialPhase.TAP_CELL
                when (step?.kind) {
                    SudokuTutorialKind.ROW -> if (tap) R.string.sudoku_tut_row_tap else R.string.sudoku_tut_row_digit
                    SudokuTutorialKind.COLUMN -> if (tap) R.string.sudoku_tut_col_tap else R.string.sudoku_tut_col_digit
                    SudokuTutorialKind.BLOCK -> if (tap) R.string.sudoku_tut_block_tap else R.string.sudoku_tut_block_digit
                    SudokuTutorialKind.LAST_REMAINING -> if (tap) R.string.sudoku_tut_last_tap else R.string.sudoku_tut_trick_digit
                    SudokuTutorialKind.CROSS_HATCH -> if (tap) R.string.sudoku_tut_cross_tap else R.string.sudoku_tut_trick_digit
                    null -> R.string.sudoku_tut_card_intro_1
                }
            }
        }
    }
}
