package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.Puzzle

/**
 * The ONE rule for when the mouse booster becomes available: numbered levels from
 * [UNLOCK_LEVEL] (Lv 10) onward; locked for Lv 1-9. Daily Challenge keeps the slot locked.
 * Pure, no Android.
 */
object MouseBoosterPolicy {
    const val UNLOCK_LEVEL = 10

    /** X marks one use puts on the board (fewer when fewer eligible cells exist). */
    const val MARKS_PER_USE = 3

    fun isUnlocked(level: Int): Boolean = level >= UNLOCK_LEVEL
}

/**
 * Picks the cells the Mouse booster crosses out: up to [MouseBoosterPolicy.MARKS_PER_USE] random cells that are
 * NOT solution cells (so an X there can never be wrong) and currently have no mark. Randomness comes from the
 * caller's [SeededRng], so results are reproducible in tests. Result is sorted by flat index.
 */
class MouseBoosterEngine {

    fun pick(
        puzzle: Puzzle,
        board: BoardState,
        rng: SeededRng,
        count: Int = MouseBoosterPolicy.MARKS_PER_USE,
    ): List<Pos> {
        val n = puzzle.size
        require(board.size == n) { "board size" }
        val eligible = ArrayList<Int>()
        for (i in 0 until n * n) {
            if (board.marks[i] == CellMark.EMPTY && puzzle.solution[i / n] != i % n) eligible.add(i)
        }
        if (eligible.isEmpty()) return emptyList()
        rng.shuffle(eligible)
        return eligible.take(count).sorted().map { Pos.fromIndex(it, n) }
    }
}
