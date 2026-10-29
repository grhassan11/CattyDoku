package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.Puzzle

/**
 * Picks the cat the Cat booster places: the solution cell of the most-forced unfinished unit
 * (region / row / column with the fewest candidates; ties: region, row, column, then lowest index).
 * Candidates derive from correct cats only. Result is always a solution cell with no cat yet,
 * or null when every unit is already finished.
 */
class CatBoosterEngine {

    fun pick(puzzle: Puzzle, board: BoardState): Pos? {
        val n = puzzle.size
        require(board.size == n) { "board size" }
        val grid = CandidateGrid.fromCorrectCats(puzzle, board)
        val unit = grid.mostForcedUnit() ?: return null
        for (cell in grid.unitCells(unit.first, unit.second)) {
            if (puzzle.solution[cell / n] == cell % n) return Pos.fromIndex(cell, n)
        }
        return null
    }
}
