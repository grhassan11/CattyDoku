package com.tm.meow.sudoku.puzzle.domain.sudoku.tutorial

import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuSize

/** INTRO = lesson start dialog; TAP_CELL / ENTER_DIGIT = the two halves of a step; LESSON_COMPLETE = end dialog. */
enum class SudokuTutorialPhase { INTRO, TAP_CELL, ENTER_DIGIT, LESSON_COMPLETE }

/**
 * In-memory tutorial position for one board [size]. [values] is the lesson board (size.cells digits, 0 = empty); it only ever holds the script's
 * digits, never a real game. [solved] counts correct steps so far (UI success toast trigger).
 */
data class SudokuTutorialState(
    val size: SudokuSize = SudokuSize.S9,
    val lesson: Int = 1,
    val step: Int = 0,
    val phase: SudokuTutorialPhase = SudokuTutorialPhase.INTRO,
    val values: List<Int> = SudokuTutorialScript.lesson(size, lesson).initial,
    val solved: Int = 0,
) {
    val currentStep: SudokuTutorialStep? get() = SudokuTutorialScript.lesson(size, lesson).steps.getOrNull(step)
    val stepCount: Int get() = SudokuTutorialScript.lesson(size, lesson).steps.size
}

/** Pure reducer. Wrong taps and wrong digits are ignored (state returned unchanged). */
object SudokuTutorialEngine {

    fun initial(size: SudokuSize = SudokuSize.S9): SudokuTutorialState = SudokuTutorialState(size = size)

    /** "Start Lesson". */
    fun start(s: SudokuTutorialState): SudokuTutorialState =
        if (s.phase == SudokuTutorialPhase.INTRO) s.copy(phase = SudokuTutorialPhase.TAP_CELL) else s

    fun tapCell(s: SudokuTutorialState, cell: Int): SudokuTutorialState {
        val step = s.currentStep ?: return s
        return if (s.phase == SudokuTutorialPhase.TAP_CELL && cell == step.cell) {
            s.copy(phase = SudokuTutorialPhase.ENTER_DIGIT)
        } else {
            s
        }
    }

    fun enterDigit(s: SudokuTutorialState, digit: Int): SudokuTutorialState {
        val step = s.currentStep ?: return s
        if (s.phase != SudokuTutorialPhase.ENTER_DIGIT || digit != step.digit) return s
        val placed = s.values.toMutableList().also { it[step.cell] = digit }
        val last = s.step >= s.stepCount - 1
        return s.copy(
            values = placed,
            step = if (last) s.step else s.step + 1,
            phase = if (last) SudokuTutorialPhase.LESSON_COMPLETE else SudokuTutorialPhase.TAP_CELL,
            solved = s.solved + 1,
        )
    }

    /** Lesson complete -> next lesson intro. Null when there is no next lesson (caller leaves the tutorial). */
    fun nextLesson(s: SudokuTutorialState): SudokuTutorialState? {
        if (s.phase != SudokuTutorialPhase.LESSON_COMPLETE || s.lesson >= SudokuTutorialScript.LESSON_COUNT) return null
        val next = s.lesson + 1
        return SudokuTutorialState(
            size = s.size,
            lesson = next,
            step = 0,
            phase = SudokuTutorialPhase.INTRO,
            values = SudokuTutorialScript.lesson(s.size, next).initial,
            solved = s.solved,
        )
    }
}
