package com.threetech.catpuzzle.domain.sudoku.engine

import com.threetech.catpuzzle.domain.engine.SeededRng
import com.threetech.catpuzzle.domain.sudoku.model.SudokuPuzzle
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTechnique
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTier

/**
 * Bump whenever generator output for a given (tier, seed) changes
 * (RNG use, fill/removal order, grading, tier tables in SudokuTier).
 */
const val SUDOKU_GENERATOR_VERSION: Int = 1

/**
 * Pure, blocking, deterministic: same (tier, seed, version) => same puzzle. Call off the main thread.
 *
 * Per attempt: random complete grid, then remove cells in seeded order keeping a unique solution.
 * Once clues <= tier.maxClues each removal is graded by the technique solver; removals that push the
 * grade above the tier band are undone. Stops at a seeded target clue count when grade >= band minimum.
 * Bounded by [MAX_ATTEMPTS] (count, not time, so output stays deterministic); if no attempt lands in
 * the tier, the closest valid unique puzzle is returned with matchedTier = false.
 */
object SudokuGenerator {
    const val MAX_ATTEMPTS = 24

    private class Candidate(val givens: IntArray, val solution: IntArray, val rating: SudokuTechnique, val dist: Int)

    fun generate(tier: SudokuTier, seed: Long): SudokuPuzzle {
        val master = SeededRng(seed xor (tier.ordinal + 1) * -0x61c8864680b583ebL)
        var best: Candidate? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            val c = attempt(tier, SeededRng(master.nextLong()))
            if (best == null || c.dist < best.dist) best = c
            if (c.dist == 0) break
        }
        val b = best!!
        return SudokuPuzzle(tier, b.givens, b.solution, seed, SUDOKU_GENERATOR_VERSION, b.rating, b.dist == 0)
    }

    private fun attempt(tier: SudokuTier, rng: SeededRng): Candidate {
        val size = tier.size
        val cells = size.cells
        val solution = checkNotNull(SudokuSolver.solve(size, IntArray(cells), rng)) { "empty grid unsolvable" }
        val grid = solution.copyOf()
        val order = IntArray(cells) { it }
        rng.shuffle(order)
        val target = tier.minClues + rng.nextInt(tier.maxClues - tier.minClues + 1)
        var clues = cells
        for (cell in order) {
            if (clues <= tier.minClues) break
            val d = grid[cell]
            grid[cell] = 0
            if (SudokuSolver.countSolutions(size, grid, 2) != 1) { grid[cell] = d; continue }
            clues--
            if (clues > tier.maxClues) continue
            val t = SudokuSolver.grade(size, grid)
            if (t > tier.maxTechnique) { grid[cell] = d; clues++; continue }
            if (clues <= target && t >= tier.minTechnique) break
        }
        val rating = SudokuSolver.grade(size, grid)
        val techGap = when {
            rating < tier.minTechnique -> tier.minTechnique.ordinal - rating.ordinal
            rating > tier.maxTechnique -> rating.ordinal - tier.maxTechnique.ordinal
            else -> 0
        }
        val clueGap = when {
            clues < tier.minClues -> tier.minClues - clues
            clues > tier.maxClues -> clues - tier.maxClues
            else -> 0
        }
        return Candidate(grid, solution, rating, techGap * 100 + clueGap)
    }
}
