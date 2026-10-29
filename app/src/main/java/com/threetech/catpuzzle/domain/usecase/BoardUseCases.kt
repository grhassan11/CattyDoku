package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.engine.GameEngine
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.Puzzle
import com.threetech.catpuzzle.domain.model.StepResult

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

/** Explicit cat removal (no-op when the cell holds no cat). */
class RemoveCat(private val engine: GameEngine) {
    operator fun invoke(puzzle: Puzzle, state: GameState, pos: Pos): StepResult =
        engine.removeCat(puzzle, state, pos)
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
