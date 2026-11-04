package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.engine.GameEngine
import com.tm.meow.sudoku.puzzle.domain.model.GameState
import com.tm.meow.sudoku.puzzle.domain.model.Pos
import com.tm.meow.sudoku.puzzle.domain.model.Puzzle
import com.tm.meow.sudoku.puzzle.domain.model.StepResult

/** Tap: toggle X on a cell. */
class ToggleX(private val engine: GameEngine) {
    operator fun invoke(puzzle: Puzzle, state: GameState, pos: Pos): StepResult =
        engine.applyTap(puzzle, state, pos)
}

/** Double tap: place a cat (replaces X); on a cat cell it removes the cat. */
class PlaceCat(private val engine: GameEngine) {
    operator fun invoke(puzzle: Puzzle, state: GameState, pos: Pos): StepResult =
        engine.applyDoubleTap(puzzle, state, pos)
}

/** Drag across cells: MARK or ERASE X, mode decided by the first cell. One undo step. */
class ApplyDragX(private val engine: GameEngine) {
    operator fun invoke(puzzle: Puzzle, state: GameState, path: List<Pos>): StepResult =
        engine.applyDrag(puzzle, state, path)
}

/** Undo the last user op. No redo, no heart refund. */
class Undo(private val engine: GameEngine) {
    operator fun invoke(state: GameState): StepResult = engine.undo(state)
}
