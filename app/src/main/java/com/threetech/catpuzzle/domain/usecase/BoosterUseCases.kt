package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.engine.CatBoosterEngine
import com.threetech.catpuzzle.domain.engine.GameEngine
import com.threetech.catpuzzle.domain.engine.HintEngine
import com.threetech.catpuzzle.domain.engine.MouseBoosterEngine
import com.threetech.catpuzzle.domain.engine.SeededRng
import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.Hint
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.Puzzle
import com.threetech.catpuzzle.domain.model.StepResult

sealed interface HintResult {
    /** [state] has one charge spent and hintsUsed + 1, except when [hint] is [Hint.None] (free, unchanged). */
    data class Granted(val state: GameState, val hint: Hint) : HintResult

    data object NoCharge : HintResult

    /** Game already finished. */
    data object Unavailable : HintResult
}

sealed interface CatBoosterResult {
    data class Placed(val step: StepResult, val cell: Pos) : CatBoosterResult

    data object NoCharge : CatBoosterResult

    /** Game finished or nothing left to place. */
    data object Unavailable : CatBoosterResult
}

sealed interface MouseBoosterResult {
    /** [state] has the [cells] crossed out (X) and one MOUSE charge spent. Never empty. */
    data class Marked(val state: GameState, val cells: List<Pos>) : MouseBoosterResult

    data object NoCharge : MouseBoosterResult

    /** Game finished, or no eligible cell left (nothing marked, no charge spent). */
    data object Unavailable : MouseBoosterResult
}

/** Hint booster: spends a HINT charge and counts toward the score penalty. */
class UseHint(private val hintEngine: HintEngine) {
    operator fun invoke(puzzle: Puzzle, state: GameState): HintResult {
        if (!state.isPlaying) return HintResult.Unavailable
        if (state.chargesOf(Booster.HINT) <= 0) return HintResult.NoCharge
        val hint = hintEngine.next(puzzle, state.board)
        if (hint is Hint.None) return HintResult.Granted(state, hint)
        val spent = state.withCharge(Booster.HINT, -1).copy(hintsUsed = state.hintsUsed + 1)
        return HintResult.Granted(spent, hint)
    }
}

/** Cat booster: places the most-forced correct cat. Costs a CAT charge, never a heart, no score penalty. */
class UseCatBooster(
    private val catBooster: CatBoosterEngine,
    private val engine: GameEngine,
) {
    operator fun invoke(puzzle: Puzzle, state: GameState): CatBoosterResult {
        if (!state.isPlaying) return CatBoosterResult.Unavailable
        if (state.chargesOf(Booster.CAT) <= 0) return CatBoosterResult.NoCharge
        val cell = catBooster.pick(puzzle, state.board) ?: return CatBoosterResult.Unavailable
        val step = engine.placeBoosted(puzzle, state, cell)
        if (step.state === state) return CatBoosterResult.Unavailable
        return CatBoosterResult.Placed(
            step = StepResult(step.state.withCharge(Booster.CAT, -1), step.events),
            cell = cell,
        )
    }
}

/**
 * Mouse booster: puts X on up to 3 random empty cells that cannot hold a cat. Costs a MOUSE charge only when at
 * least one cell was marked; never costs a heart, no score penalty, not undoable (like the Cat booster).
 * [seedSource] supplies a fresh seed per use (injected so tests are deterministic).
 */
class UseMouseBooster(
    private val mouseBooster: MouseBoosterEngine,
    private val seedSource: () -> Long,
) {
    operator fun invoke(puzzle: Puzzle, state: GameState): MouseBoosterResult {
        if (!state.isPlaying) return MouseBoosterResult.Unavailable
        if (state.chargesOf(Booster.MOUSE) <= 0) return MouseBoosterResult.NoCharge
        val cells = mouseBooster.pick(puzzle, state.board, SeededRng(seedSource()))
        if (cells.isEmpty()) return MouseBoosterResult.Unavailable
        val n = puzzle.size
        val marks = state.board.marks.toMutableList()
        for (c in cells) marks[c.index(n)] = CellMark.X
        val next = state.copy(board = BoardState(n, marks)).withCharge(Booster.MOUSE, -1)
        return MouseBoosterResult.Marked(next, cells)
    }
}
