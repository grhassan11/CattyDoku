package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.Pos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MouseBoosterEngineTest {
    private val engine = MouseBoosterEngine()
    private val puzzle = GameFixtures.unique4()

    @Test
    fun policyUnlocksFromLevel10() {
        assertFalse(MouseBoosterPolicy.isUnlocked(9))
        assertTrue(MouseBoosterPolicy.isUnlocked(10))
        assertTrue(MouseBoosterPolicy.isUnlocked(11))
        assertEquals(10, MouseBoosterPolicy.UNLOCK_LEVEL)
        assertFalse(MouseBoosterPolicy.isUnlocked(0))
        assertFalse(MouseBoosterPolicy.isUnlocked(-5))
    }

    @Test
    fun picksThreeDistinctNonSolutionEmptyCells() {
        for (seed in 1L..40L) {
            val cells = engine.pick(puzzle, BoardState.empty(4), SeededRng(seed))
            assertEquals(3, cells.size)
            assertEquals(3, cells.toSet().size)
            cells.forEach { assertFalse(GameFixtures.isSolution(puzzle, it)) }
        }
    }

    @Test
    fun deterministicForSameSeedAndVariesAcrossSeeds() {
        val a = engine.pick(puzzle, BoardState.empty(4), SeededRng(7))
        val b = engine.pick(puzzle, BoardState.empty(4), SeededRng(7))
        assertEquals(a, b)
        val distinct = (1L..30L).map { engine.pick(puzzle, BoardState.empty(4), SeededRng(it)) }.toSet()
        assertTrue(distinct.size > 1)
    }

    @Test
    fun skipsMarkedCells() {
        var board = BoardState.empty(4)
        val marked = mutableSetOf<Pos>()
        var left = 2 // leave 2 eligible
        for (r in 0 until 4) for (c in 0 until 4) {
            val p = Pos(r, c)
            if (GameFixtures.isSolution(puzzle, p)) continue
            if (left > 0) {
                left--
                continue
            }
            board = board.withMark(r, c, if ((r + c) % 2 == 0) CellMark.X else CellMark.CAT)
            marked.add(p)
        }
        val cells = engine.pick(puzzle, board, SeededRng(3))
        assertEquals(2, cells.size)
        cells.forEach { assertFalse(it in marked) }
    }

    @Test
    fun solutionCellsAreNeverPickedEvenWhenEmpty() {
        val cells = engine.pick(puzzle, BoardState.empty(4), SeededRng(1), count = 100)
        assertEquals(12, cells.size) // 16 - 4 solution cells
        puzzle.solutionCells().forEach { assertFalse(it in cells) }
    }

    @Test
    fun noEligibleCellGivesEmpty() {
        var board = BoardState.empty(4)
        for (r in 0 until 4) for (c in 0 until 4) {
            if (!GameFixtures.isSolution(puzzle, Pos(r, c))) board = board.withMark(r, c, CellMark.X)
        }
        assertTrue(engine.pick(puzzle, board, SeededRng(1)).isEmpty())
    }
}
