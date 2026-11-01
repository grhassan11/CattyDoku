package com.threetech.catpuzzle.domain.sudoku.engine

import com.threetech.catpuzzle.domain.sudoku.model.SudokuGeometry
import com.threetech.catpuzzle.domain.sudoku.model.SudokuHint
import com.threetech.catpuzzle.domain.sudoku.model.SudokuSize
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTechnique
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTier

/** Validity checks. A mistake = digit differs from the solution; a conflict = rule duplicate in a unit. */
object SudokuValidator {
    fun isCorrect(solution: IntArray, cell: Int, digit: Int): Boolean = solution[cell] == digit

    /** Cells whose digit also appears in another cell of a shared unit. */
    fun conflicts(size: SudokuSize, values: IntArray): BooleanArray {
        val g = SudokuGeometry.of(size)
        val out = BooleanArray(g.cells)
        for (c in 0 until g.cells) {
            if (values[c] == 0) continue
            for (p in g.peers[c]) if (values[p] == values[c]) { out[c] = true; break }
        }
        return out
    }

    fun isSolved(values: IntArray, solution: IntArray): Boolean = values.contentEquals(solution)

    /** Complete grid obeying all unit rules. */
    fun isValidSolution(size: SudokuSize, values: IntArray): Boolean =
        values.size == size.cells && values.all { it in 1..size.n } && conflicts(size, values).none { it }
}

/**
 * Score: starts 0; + [POINTS_PER_DIGIT] x tier multiplier per correct digit; - [MISTAKE_PENALTY] per mistake
 * (running score never below 0); + time bonus on win = seconds under tier par x multiplier.
 */
object SudokuScoreCalculator {
    const val POINTS_PER_DIGIT = 10
    const val MISTAKE_PENALTY = 15
    const val MAX_MISTAKES = 3

    fun digitPoints(tier: SudokuTier): Int = POINTS_PER_DIGIT * tier.scoreMultiplier

    fun timeBonus(tier: SudokuTier, elapsedMs: Long): Int =
        ((tier.parSeconds - elapsedMs / 1000).coerceAtLeast(0) * tier.scoreMultiplier).toInt()

    /** Running score after a correct placement / mistake. */
    fun afterCorrect(score: Int, tier: SudokuTier): Int = score + digitPoints(tier)
    fun afterMistake(score: Int): Int = (score - MISTAKE_PENALTY).coerceAtLeast(0)

    fun finalScore(tier: SudokuTier, correctDigits: Int, mistakes: Int, elapsedMs: Long): Int =
        (correctDigits * digitPoints(tier) - mistakes * MISTAKE_PENALTY).coerceAtLeast(0) + timeBonus(tier, elapsedMs)
}

/** Next-move hint. Based on the player's CORRECT entries only; never contradicts the solution. */
object SudokuHintFinder {
    /**
     * [values] = current board (wrong entries allowed). [selected] empty or wrong cell -> its solution digit.
     * Otherwise best cell: naked single, then hidden single, else the most constrained empty cell
     * (technique GUESS = plain reveal). Null when nothing to hint (board solved).
     */
    fun find(size: SudokuSize, solution: IntArray, values: IntArray, selected: Int?): SudokuHint? {
        val cells = size.cells
        if (selected != null && selected in 0 until cells && values[selected] != solution[selected]) {
            return SudokuHint(selected, solution[selected], tech(size, solution, values, selected))
        }
        val correct = IntArray(cells) { if (values[it] == solution[it]) values[it] else 0 }
        if (correct.all { it != 0 }) return null
        val logic = SudokuLogic(SudokuGeometry.of(size), correct)
        val ns = logic.nakedSingle()
        if (ns >= 0) return SudokuHint(ns shr 4, ns and 15, SudokuTechnique.NAKED_SINGLE)
        val hs = logic.hiddenSingle()
        if (hs >= 0) return SudokuHint(hs shr 4, hs and 15, SudokuTechnique.HIDDEN_SINGLE)
        var best = -1
        for (c in 0 until cells) {
            if (correct[c] != 0) continue
            if (best < 0 || logic.cand[c].countOneBits() < logic.cand[best].countOneBits()) best = c
        }
        return SudokuHint(best, solution[best], SudokuTechnique.GUESS)
    }

    private fun tech(size: SudokuSize, solution: IntArray, values: IntArray, cell: Int): SudokuTechnique {
        val correct = IntArray(size.cells) { if (values[it] == solution[it]) values[it] else 0 }
        val logic = SudokuLogic(SudokuGeometry.of(size), correct)
        val bit = 1 shl (solution[cell] - 1)
        if (logic.cand[cell] == bit) return SudokuTechnique.NAKED_SINGLE
        val g = SudokuGeometry.of(size)
        for (u in g.unitsOf[cell]) if (g.units[u].count { logic.cand[it] and bit != 0 } == 1) return SudokuTechnique.HIDDEN_SINGLE
        return SudokuTechnique.GUESS
    }
}
