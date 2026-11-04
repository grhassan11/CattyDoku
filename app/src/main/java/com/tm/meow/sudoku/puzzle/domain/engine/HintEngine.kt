package com.tm.meow.sudoku.puzzle.domain.engine

import com.tm.meow.sudoku.puzzle.domain.model.BoardState
import com.tm.meow.sudoku.puzzle.domain.model.CellMark
import com.tm.meow.sudoku.puzzle.domain.model.Hint
import com.tm.meow.sudoku.puzzle.domain.model.HintRule
import com.tm.meow.sudoku.puzzle.domain.model.HintTechnique
import com.tm.meow.sudoku.puzzle.domain.model.MistakeKind
import com.tm.meow.sudoku.puzzle.domain.model.Pos
import com.tm.meow.sudoku.puzzle.domain.model.Puzzle

/**
 * Pure hint picker. Candidates come from the player's correct cats only; wrong X marks are ignored.
 *
 * Order: wrong cat, X on a solution cell, naked single, confinement, crowding k=2,
 * dead-end look-ahead, crowding k=3..4, reveal fallback.
 * Eliminations the player already X-marked are skipped (applied internally, not hinted).
 * Hints never contradict the stored solution. Solved board => [Hint.None].
 */
class HintEngine(private val catBooster: CatBoosterEngine = CatBoosterEngine()) {

    fun next(puzzle: Puzzle, board: BoardState): Hint {
        val n = puzzle.size
        require(board.size == n) { "board size" }

        for (i in 0 until n * n) {
            if (board.marks[i] == CellMark.CAT && puzzle.solution[i / n] != i % n) {
                return Hint.Mistake(Pos.fromIndex(i, n), MistakeKind.WRONG_CAT)
            }
        }
        for (row in 0 until n) {
            val idx = row * n + puzzle.solution[row]
            if (board.marks[idx] == CellMark.X) {
                return Hint.Mistake(Pos.fromIndex(idx, n), MistakeKind.X_ON_SOLUTION)
            }
        }

        val grid = CandidateGrid.fromCorrectCats(puzzle, board)
        if (grid.catCount == n) return Hint.None

        val finders: List<Pair<HintTechnique, (CandidateGrid) -> Elimination?>> = listOf(
            HintTechnique.CONFINEMENT to { g -> g.hall(1) },
            HintTechnique.CROWDING_PAIR to { g -> g.hall(2) },
            HintTechnique.LOOKAHEAD to { g -> g.lookahead() },
            HintTechnique.CROWDING_HALL to { g -> g.hall(3) ?: g.hall(4) },
        )

        while (true) {
            val single = grid.nakedSingleDetail()
            if (single != null) {
                return Hint.Place(
                    target = Pos.fromIndex(single.cell, n),
                    technique = HintTechnique.NAKED_SINGLE,
                    rule = HintRule.fromFamily(single.family),
                    focus = grid.unitCells(single.family, single.line).map { Pos.fromIndex(it, n) },
                )
            }
            var progressed = false
            for ((technique, find) in finders) {
                val step = find(grid) ?: continue
                val fresh = step.targets.filter { board.marks[it] != CellMark.X }
                if (fresh.isNotEmpty()) {
                    return Hint.Eliminate(
                        targets = fresh.map { Pos.fromIndex(it, n) },
                        reasons = step.reasons.map { Pos.fromIndex(it, n) },
                        technique = technique,
                        rule = step.rule,
                        focus = step.focus.map { Pos.fromIndex(it, n) },
                    )
                }
                grid.eliminate(step.targets)
                progressed = true
                break
            }
            if (!progressed) break
        }

        val reveal = catBooster.pick(puzzle, board) ?: return Hint.None
        return Hint.Place(reveal, HintTechnique.REVEAL)
    }
}
