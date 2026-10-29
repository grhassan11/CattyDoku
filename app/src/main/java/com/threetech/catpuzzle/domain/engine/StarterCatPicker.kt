package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.Puzzle

/**
 * Picks the free "starter" cat pre-placed when a level starts. Pure, deterministic, from the puzzle only.
 *
 * For every solution cell (one per row) the cat is pre-placed and [LogicSolver] solves the rest.
 * Best candidate = lowest hardest-technique level needed; tie-break: most cells eliminated by the
 * placement itself (row, column, region, 8 neighbours); then lowest row (== lowest row/col, since the
 * solution has one cat per row). A candidate the solver cannot finish rates [Technique.MAX] + 1
 * (fallback heuristic: such candidates are ranked by eliminations only).
 *
 * Cost: at most 7 solver runs on a board of at most 7x7. Call off the main thread.
 * Ratings ([Puzzle.techniqueLevel]) are NOT recomputed with the starter cat: they stay as generated.
 * Open so tests can swap in a "no starter" picker; the tutorial simply never calls this.
 */
open class StarterCatPicker {

    /** The starter cell, or null only for a malformed puzzle (empty solution). */
    open fun pick(puzzle: Puzzle): Pos? {
        val n = puzzle.size
        var best = -1
        var bestLevel = Int.MAX_VALUE
        var bestElim = -1
        for (row in 0 until n) {
            val idx = row * n + puzzle.solution[row]
            val result = LogicSolver.solve(n, puzzle.regions, idx)
            val level = if (result.solved) result.maxLevel else Technique.MAX + 1
            val elim = eliminationsOf(n, puzzle.regions, idx)
            if (level < bestLevel || (level == bestLevel && elim > bestElim)) {
                best = idx
                bestLevel = level
                bestElim = elim
            }
        }
        return if (best < 0) null else Pos.fromIndex(best, n)
    }

    /** Distinct other cells that share row, column, region or an 8-neighbour with [idx]. */
    private fun eliminationsOf(n: Int, regions: IntArray, idx: Int): Int {
        val r = idx / n
        val c = idx % n
        var count = 0
        for (cell in 0 until n * n) {
            if (cell == idx) continue
            val cr = cell / n
            val cc = cell % n
            val touches = cr == r || cc == c || regions[cell] == regions[idx] ||
                (kotlin.math.abs(cr - r) <= 1 && kotlin.math.abs(cc - c) <= 1)
            if (touches) count++
        }
        return count
    }
}
