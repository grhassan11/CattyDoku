package com.threetech.catpuzzle.domain.sudoku.model

enum class SudokuStatus { PLAYING, SOLVED, GAME_OVER }

/**
 * One undoable action. [cells] are the cells the action touched, [prevValues]/[prevNotes] their content BEFORE it
 * (parallel arrays). [scoreGain] = points the action added, taken back on undo (0 for mistakes, hints, notes).
 */
class SudokuUndoStep(
    val cells: IntArray,
    val prevValues: IntArray,
    val prevNotes: IntArray,
    val scoreGain: Int,
)

/**
 * Immutable game snapshot. Arrays are never mutated after construction (the engine copies on write).
 * Cell index = r * n + c. [values] 0 = empty. [notes] = per-cell bitmask, bit (d - 1) set = pencil digit d.
 * [selected] = -1 for none. [elapsedMs] is fed in by the caller (no clock in the domain).
 * [paused] is not persisted; a restored game starts unpaused.
 */
data class SudokuGameState(
    val puzzle: SudokuPuzzle,
    val values: IntArray,
    val notes: IntArray,
    val selected: Int = NO_SELECTION,
    val notesMode: Boolean = false,
    val undo: List<SudokuUndoStep> = emptyList(),
    val hearts: Int = MAX_HEARTS,
    val mistakes: Int = 0,
    val hintsUsed: Int = 0,
    val score: Int = 0,
    val elapsedMs: Long = 0L,
    val paused: Boolean = false,
    val status: SudokuStatus = SudokuStatus.PLAYING,
) {
    val tier: SudokuTier get() = puzzle.tier
    val size: SudokuSize get() = puzzle.size

    /** Accepts input: still playing and not paused. */
    val isActive: Boolean get() = status == SudokuStatus.PLAYING && !paused
    val canUndo: Boolean get() = isActive && undo.isNotEmpty()
    fun isGiven(cell: Int): Boolean = puzzle.givens[cell] != 0

    companion object {
        const val NO_SELECTION = -1
        const val MAX_HEARTS = 3
    }
}
