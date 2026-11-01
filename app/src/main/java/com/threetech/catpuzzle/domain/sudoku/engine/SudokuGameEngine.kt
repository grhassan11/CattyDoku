package com.threetech.catpuzzle.domain.sudoku.engine

import com.threetech.catpuzzle.domain.sudoku.model.SudokuGameState
import com.threetech.catpuzzle.domain.sudoku.model.SudokuGeometry
import com.threetech.catpuzzle.domain.sudoku.model.SudokuHint
import com.threetech.catpuzzle.domain.sudoku.model.SudokuPuzzle
import com.threetech.catpuzzle.domain.sudoku.model.SudokuStatus
import com.threetech.catpuzzle.domain.sudoku.model.SudokuUndoStep

/**
 * Pure reducer over [SudokuGameState]. No clock: callers pass elapsed ms (monotonic, never decreases here).
 * Every function returns the SAME instance when the action is a no-op.
 *
 * Rules:
 *  - Input only while [SudokuGameState.isActive] (PLAYING and not paused).
 *  - Givens are immutable. A correct digit locks its cell (no erase, no overwrite) so points cannot be farmed.
 *  - Wrong digit (differs from the solution) stays on the board, costs a heart + a score penalty;
 *    0 hearts = GAME_OVER. It can be erased, or overwritten by another digit (another mistake if wrong again).
 *  - Correct digit (or hint) clears that digit from row/col/box peer notes (and the cell's own notes).
 *  - Undo is unlimited and never refunds hearts, mistakes or hint use; it takes back digit points it earned.
 *  - Win = values equal the solution; adds the time bonus once.
 */
object SudokuGameEngine {

    fun newGame(puzzle: SudokuPuzzle): SudokuGameState =
        SudokuGameState(puzzle = puzzle, values = puzzle.givens.copyOf(), notes = IntArray(puzzle.size.cells))

    /** Heal a restored state: solved board becomes SOLVED, 0 hearts becomes GAME_OVER. No bonus is added. */
    fun normalize(state: SudokuGameState): SudokuGameState = when {
        state.status != SudokuStatus.PLAYING -> state
        state.values.contentEquals(state.puzzle.solution) -> state.copy(status = SudokuStatus.SOLVED, paused = false)
        state.hearts <= 0 -> state.copy(status = SudokuStatus.GAME_OVER, paused = false)
        else -> state
    }

    /** Same puzzle, fresh attempt: givens only, hearts/score/time/hints reset. */
    fun restart(state: SudokuGameState): SudokuGameState = newGame(state.puzzle)

    fun select(state: SudokuGameState, cell: Int): SudokuGameState {
        if (!state.isActive || cell !in 0 until state.size.cells || cell == state.selected) return state
        return state.copy(selected = cell)
    }

    fun toggleNotesMode(state: SudokuGameState): SudokuGameState =
        if (!state.isActive) state else state.copy(notesMode = !state.notesMode)

    fun enterDigit(state: SudokuGameState, digit: Int, elapsedMs: Long = state.elapsedMs): SudokuGameState {
        if (!state.isActive) return state
        val cell = state.selected
        val n = state.size.n
        if (cell !in 0 until state.size.cells || digit !in 1..n || state.isGiven(cell)) return state
        val current = state.values[cell]
        val bit = 1 shl (digit - 1)

        if (state.notesMode) {
            if (current != 0) return state
            val notes = state.notes.copyOf()
            notes[cell] = notes[cell] xor bit
            val step = SudokuUndoStep(intArrayOf(cell), intArrayOf(0), intArrayOf(state.notes[cell]), 0)
            return state.copy(notes = notes, undo = state.undo + step, elapsedMs = maxOf(state.elapsedMs, elapsedMs))
        }

        val solution = state.puzzle.solution
        if (current == solution[cell] || current == digit) return state
        val el = maxOf(state.elapsedMs, elapsedMs)

        if (digit == solution[cell]) {
            return place(state, cell, digit, el, gain = SudokuScoreCalculator.digitPoints(state.tier), hint = false)
        }

        // Mistake: digit stays visible (red) until erased or replaced.
        val values = state.values.copyOf()
        values[cell] = digit
        val notes = state.notes.copyOf()
        notes[cell] = 0
        val hearts = (state.hearts - 1).coerceAtLeast(0)
        val step = SudokuUndoStep(intArrayOf(cell), intArrayOf(current), intArrayOf(state.notes[cell]), 0)
        return state.copy(
            values = values,
            notes = notes,
            undo = state.undo + step,
            hearts = hearts,
            mistakes = state.mistakes + 1,
            score = SudokuScoreCalculator.afterMistake(state.score),
            elapsedMs = el,
            status = if (hearts == 0) SudokuStatus.GAME_OVER else SudokuStatus.PLAYING,
        )
    }

    /** Clears a wrong digit, or the notes of an empty cell. Givens and correct digits are untouched. */
    fun erase(state: SudokuGameState): SudokuGameState {
        if (!state.isActive) return state
        val cell = state.selected
        if (cell !in 0 until state.size.cells || state.isGiven(cell)) return state
        val current = state.values[cell]
        if (current != 0 && current == state.puzzle.solution[cell]) return state
        if (current == 0 && state.notes[cell] == 0) return state
        val values = state.values.copyOf()
        values[cell] = 0
        val notes = state.notes.copyOf()
        notes[cell] = 0
        val step = SudokuUndoStep(intArrayOf(cell), intArrayOf(current), intArrayOf(state.notes[cell]), 0)
        return state.copy(values = values, notes = notes, undo = state.undo + step)
    }

    fun undo(state: SudokuGameState): SudokuGameState {
        if (!state.canUndo) return state
        val step = state.undo.last()
        val values = state.values.copyOf()
        val notes = state.notes.copyOf()
        for (i in step.cells.indices) {
            values[step.cells[i]] = step.prevValues[i]
            notes[step.cells[i]] = step.prevNotes[i]
        }
        val next = state.copy(
            values = values,
            notes = notes,
            undo = state.undo.dropLast(1),
            selected = step.cells[0],
            score = (state.score - step.scoreGain).coerceAtLeast(0),
        )
        return if (values.contentEquals(state.puzzle.solution)) next.copy(status = SudokuStatus.SOLVED) else next
    }

    /**
     * Apply [hint] (digit revealed in its cell, hintsUsed + 1, no digit points, no heart cost). Charge spending is the
     * caller's job (see UseSudokuHint). Undoable, but undo does not refund the charge.
     */
    fun applyHint(state: SudokuGameState, hint: SudokuHint, elapsedMs: Long = state.elapsedMs): SudokuGameState {
        if (!state.isActive) return state
        if (hint.cell !in 0 until state.size.cells || state.isGiven(hint.cell)) return state
        if (hint.digit != state.puzzle.solution[hint.cell] || state.values[hint.cell] == hint.digit) return state
        return place(state, hint.cell, hint.digit, maxOf(state.elapsedMs, elapsedMs), gain = 0, hint = true)
    }

    /** Update the clock while playing. Monotonic: a smaller value is ignored. */
    fun tick(state: SudokuGameState, elapsedMs: Long): SudokuGameState =
        if (!state.isActive || elapsedMs <= state.elapsedMs) state else state.copy(elapsedMs = elapsedMs)

    fun pause(state: SudokuGameState, elapsedMs: Long = state.elapsedMs): SudokuGameState {
        if (state.status != SudokuStatus.PLAYING || state.paused) return state
        return state.copy(paused = true, elapsedMs = maxOf(state.elapsedMs, elapsedMs))
    }

    fun resume(state: SudokuGameState): SudokuGameState =
        if (state.status != SudokuStatus.PLAYING || !state.paused) state else state.copy(paused = false)

    // ---- internals ----

    /** Put the CORRECT [digit] in [cell], clear it from own + peer notes, detect the win. */
    private fun place(
        state: SudokuGameState,
        cell: Int,
        digit: Int,
        elapsedMs: Long,
        gain: Int,
        hint: Boolean,
    ): SudokuGameState {
        val geometry = SudokuGeometry.of(state.size)
        val bit = 1 shl (digit - 1)
        val values = state.values.copyOf()
        val notes = state.notes.copyOf()
        val cells = ArrayList<Int>(4).apply { add(cell) }
        values[cell] = digit
        notes[cell] = 0
        for (p in geometry.peers[cell]) {
            if (notes[p] and bit != 0) {
                notes[p] = notes[p] and bit.inv()
                cells.add(p)
            }
        }
        val idx = cells.toIntArray()
        val step = SudokuUndoStep(
            cells = idx,
            prevValues = IntArray(idx.size) { state.values[idx[it]] },
            prevNotes = IntArray(idx.size) { state.notes[idx[it]] },
            scoreGain = gain,
        )
        var score = state.score + gain
        val solved = values.contentEquals(state.puzzle.solution)
        if (solved) score += SudokuScoreCalculator.timeBonus(state.tier, elapsedMs)
        return state.copy(
            values = values,
            notes = notes,
            selected = cell,
            undo = state.undo + step,
            hintsUsed = state.hintsUsed + if (hint) 1 else 0,
            score = score,
            elapsedMs = elapsedMs,
            status = if (solved) SudokuStatus.SOLVED else SudokuStatus.PLAYING,
        )
    }
}
