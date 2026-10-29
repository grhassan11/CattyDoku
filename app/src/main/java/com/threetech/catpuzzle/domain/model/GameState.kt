package com.threetech.catpuzzle.domain.model

enum class GameStatus { PLAYING, SOLVED, LOST }

/** One cell's mark before an operation. */
data class CellChange(val index: Int, val before: CellMark)

/** One undoable user operation (a tap, a double tap or a whole drag). */
data class UndoEntry(val changes: List<CellChange>)

/** Drag-mark mode, decided by the first cell of the drag. */
enum class DragMode { MARK, ERASE }

/**
 * Immutable play state of one level.
 *
 * [hearts]: starts at [MAX_HEARTS]; undo never refunds.
 * [rewardedCells]: flat indices that already paid out (or were booster-placed); stops score farming.
 * [lockedCells]: booster-placed cats; player ops and undo leave them alone.
 * [correctPlacements]: player placements that earned score (first correct placement per cell).
 * [charges]: booster charges available; missing key = 0.
 */
data class GameState(
    val board: BoardState,
    val hearts: Int = MAX_HEARTS,
    val status: GameStatus = GameStatus.PLAYING,
    val undoStack: List<UndoEntry> = emptyList(),
    val rewardedCells: Set<Int> = emptySet(),
    val lockedCells: Set<Int> = emptySet(),
    val correctPlacements: Int = 0,
    val hintsUsed: Int = 0,
    val charges: Map<Booster, Int> = emptyMap(),
) {
    val heartsLost: Int get() = MAX_HEARTS - hearts
    val isPlaying: Boolean get() = status == GameStatus.PLAYING

    fun chargesOf(booster: Booster): Int = charges[booster] ?: 0

    fun withCharge(booster: Booster, delta: Int): GameState =
        copy(charges = charges + (booster to (chargesOf(booster) + delta).coerceAtLeast(0)))

    companion object {
        const val MAX_HEARTS = 3

        fun initial(size: Int, charges: Map<Booster, Int> = emptyMap()): GameState =
            GameState(board = BoardState.empty(size), charges = charges)
    }
}

/** New state plus the events the operation raised (empty when nothing happened). */
data class StepResult(val state: GameState, val events: List<GameEvent> = emptyList())
