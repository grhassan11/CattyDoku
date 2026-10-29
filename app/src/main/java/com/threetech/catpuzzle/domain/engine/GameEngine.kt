package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.CellChange
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.DragMode
import com.threetech.catpuzzle.domain.model.GameEvent
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.GameStatus
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.Puzzle
import com.threetech.catpuzzle.domain.model.StepResult
import com.threetech.catpuzzle.domain.model.UndoEntry

/**
 * Pure reducer for board operations. Input state is never mutated; every call returns a new
 * [GameState] plus events. Ops on a finished game (SOLVED / LOST), out-of-range cells or
 * booster-locked cells are no-ops (same state, no events).
 *
 * Rules:
 * - tap = toggle X (EMPTY <-> X; a cat cell is left alone)
 * - double tap = cat toggle (cat -> removed, else cat placed over EMPTY / X)
 * - placing a cat on a non-solution cell costs one heart; 0 hearts => LOST. X never costs a heart,
 *   a correct cat never costs a heart.
 * - drag: mode from the first cell (EMPTY => MARK, X => ERASE, CAT => ignored). One undo step.
 * - undo reverts the last user op cell by cell. NO heart refund, NO score refund (rewarded cells
 *   stay rewarded, so undo + replace cannot farm points). No redo.
 * - booster cats are locked and not part of the undo stack.
 */
class GameEngine {

    fun applyTap(puzzle: Puzzle, state: GameState, pos: Pos): StepResult =
        toggleX(puzzle, state, pos)

    fun applyDoubleTap(puzzle: Puzzle, state: GameState, pos: Pos): StepResult {
        if (!usable(puzzle, state, pos)) return StepResult(state)
        return if (state.board[pos.row, pos.col] == CellMark.CAT) {
            removeCat(puzzle, state, pos)
        } else {
            placeCat(puzzle, state, pos)
        }
    }

    fun toggleX(puzzle: Puzzle, state: GameState, pos: Pos): StepResult {
        if (!usable(puzzle, state, pos)) return StepResult(state)
        val before = state.board[pos.row, pos.col]
        val after = when (before) {
            CellMark.EMPTY -> CellMark.X
            CellMark.X -> CellMark.EMPTY
            CellMark.CAT -> return StepResult(state)
        }
        return StepResult(commit(state, listOf(pos.index(puzzle.size) to after), puzzle.size))
    }

    fun placeCat(puzzle: Puzzle, state: GameState, pos: Pos): StepResult {
        if (!usable(puzzle, state, pos)) return StepResult(state)
        val n = puzzle.size
        if (state.board[pos.row, pos.col] == CellMark.CAT) return StepResult(state)
        val idx = pos.index(n)
        var next = commit(state, listOf(idx to CellMark.CAT), n)
        val events = ArrayList<GameEvent>()

        if (puzzle.solution[pos.row] == pos.col) {
            if (idx !in next.rewardedCells) {
                next = next.copy(
                    rewardedCells = next.rewardedCells + idx,
                    correctPlacements = next.correctPlacements + 1,
                )
            }
        } else {
            next = next.copy(hearts = (next.hearts - 1).coerceAtLeast(0))
            events.add(GameEvent.WrongPlacement(pos, next.hearts))
        }

        next = finish(puzzle, next, idx, events)
        return StepResult(next, events)
    }

    fun removeCat(puzzle: Puzzle, state: GameState, pos: Pos): StepResult {
        if (!usable(puzzle, state, pos)) return StepResult(state)
        if (state.board[pos.row, pos.col] != CellMark.CAT) return StepResult(state)
        return StepResult(commit(state, listOf(pos.index(puzzle.size) to CellMark.EMPTY), puzzle.size))
    }

    /** MARK or ERASE for a drag that starts on [first]; null when the drag does nothing (cat / invalid). */
    fun dragModeFor(puzzle: Puzzle, state: GameState, first: Pos): DragMode? {
        if (!inBounds(puzzle.size, first)) return null
        return when (state.board[first.row, first.col]) {
            CellMark.EMPTY -> DragMode.MARK
            CellMark.X -> DragMode.ERASE
            CellMark.CAT -> null
        }
    }

    /** Apply a whole drag path as one undo step. Mode comes from the first cell. */
    fun applyDrag(puzzle: Puzzle, state: GameState, path: List<Pos>): StepResult {
        if (!state.isPlaying || path.isEmpty()) return StepResult(state)
        val n = puzzle.size
        val mode = dragModeFor(puzzle, state, path.first()) ?: return StepResult(state)
        val from = if (mode == DragMode.MARK) CellMark.EMPTY else CellMark.X
        val to = if (mode == DragMode.MARK) CellMark.X else CellMark.EMPTY
        val seen = HashSet<Int>()
        val edits = ArrayList<Pair<Int, CellMark>>()
        for (p in path) {
            if (!inBounds(n, p)) continue
            val idx = p.index(n)
            if (idx in state.lockedCells || !seen.add(idx)) continue
            if (state.board.marks[idx] == from) edits.add(idx to to)
        }
        if (edits.isEmpty()) return StepResult(state)
        return StepResult(commit(state, edits, n))
    }

    /** Revert the last user op. Hearts and score are NOT refunded. Booster-locked cells stay. */
    fun undo(state: GameState): StepResult {
        if (!state.isPlaying || state.undoStack.isEmpty()) return StepResult(state)
        val entry = state.undoStack.last()
        val marks = state.board.marks.toMutableList()
        for (c in entry.changes) {
            if (c.index in state.lockedCells) continue
            marks[c.index] = c.before
        }
        return StepResult(
            state.copy(
                board = BoardState(state.board.size, marks),
                undoStack = state.undoStack.dropLast(1),
            ),
        )
    }

    /**
     * Booster / reveal placement of a SOLUTION cell: never costs a heart, never earns placement
     * points, is locked and not undoable. Charge accounting is the caller's job.
     */
    fun placeBoosted(puzzle: Puzzle, state: GameState, pos: Pos): StepResult {
        if (!usable(puzzle, state, pos)) return StepResult(state)
        require(puzzle.solution[pos.row] == pos.col) { "boosted cell must be a solution cell" }
        if (state.board[pos.row, pos.col] == CellMark.CAT) return StepResult(state)
        val n = puzzle.size
        val idx = pos.index(n)
        val marks = state.board.marks.toMutableList()
        marks[idx] = CellMark.CAT
        var next = state.copy(
            board = BoardState(n, marks),
            rewardedCells = state.rewardedCells + idx,
            lockedCells = state.lockedCells + idx,
        )
        val events = ArrayList<GameEvent>()
        next = finish(puzzle, next, idx, events)
        return StepResult(next, events)
    }

    /**
     * Level-start free cat. Same contract as [placeBoosted]: locked, not undoable, no heart, NO
     * placement score ([GameState.correctPlacements] untouched, cell goes into rewardedCells), no
     * charge. Kept as a separate call so a scripted tutorial board can skip it.
     */
    fun placeStarter(puzzle: Puzzle, state: GameState, pos: Pos): StepResult =
        placeBoosted(puzzle, state, pos)

    // ---- internals ----

    private fun inBounds(size: Int, p: Pos): Boolean = p.row in 0 until size && p.col in 0 until size

    private fun usable(puzzle: Puzzle, state: GameState, pos: Pos): Boolean =
        state.isPlaying &&
            inBounds(puzzle.size, pos) &&
            pos.index(puzzle.size) !in state.lockedCells

    /** Apply cell edits, recording their previous marks as one undo entry. */
    private fun commit(state: GameState, edits: List<Pair<Int, CellMark>>, size: Int): GameState {
        val marks = state.board.marks.toMutableList()
        val changes = ArrayList<CellChange>(edits.size)
        for ((idx, mark) in edits) {
            changes.add(CellChange(idx, marks[idx]))
            marks[idx] = mark
        }
        return state.copy(
            board = BoardState(size, marks),
            undoStack = state.undoStack + UndoEntry(changes),
        )
    }

    /** Conflict / Solved / Lost detection after a cat appeared on [placedIdx]. */
    private fun finish(
        puzzle: Puzzle,
        state: GameState,
        placedIdx: Int,
        events: MutableList<GameEvent>,
    ): GameState {
        val n = puzzle.size
        val analysis = BoardValidator.analyse(puzzle, state.board)
        if (placedIdx in analysis.conflictCells) {
            events.add(GameEvent.Conflict(analysis.conflictCells.sorted().map { Pos.fromIndex(it, n) }))
        }
        return when {
            analysis.solved -> {
                events.add(GameEvent.Solved)
                state.copy(status = GameStatus.SOLVED)
            }
            state.hearts <= 0 -> {
                events.add(GameEvent.Lost)
                state.copy(status = GameStatus.LOST)
            }
            else -> state
        }
    }
}
