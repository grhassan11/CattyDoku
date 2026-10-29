package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.Pos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatBoosterEngineTest {
    private val booster = CatBoosterEngine()

    @Test
    fun picksMostForcedUnitFirst() {
        // UNIQUE_4: region 0 has a single cell (0,1) => fewest candidates, region family wins ties.
        assertEquals(Pos(0, 1), booster.pick(GameFixtures.unique4(), BoardState.empty(4)))
    }

    @Test
    fun deterministic() {
        val p = GameFixtures.crowd4()
        val a = booster.pick(p, BoardState.empty(4))
        val b = booster.pick(p, BoardState.empty(4))
        assertNotNull(a)
        assertEquals(a, b)
    }

    @Test
    fun solvedBoardGivesNull() {
        val p = GameFixtures.unique4()
        assertNull(booster.pick(p, Fixtures.solutionBoard(4, p.solution)))
    }

    @Test
    fun repeatedPicksFillBoardWithSolutionCatsOnly() {
        for ((i, level) in listOf(1, 4, 7, 13, 20, 25).withIndex()) {
            val res = PuzzleGenerator.generateForLevel(level, 500L + i)
            if (res !is GenerationResult.Success) continue
            val p = res.puzzle
            var b = BoardState.empty(p.size)
            // wrong cats and wrong X marks must not confuse the pick
            val wrongRow0Col = (p.solution[0] + 2) % p.size
            b = b.withMark(0, wrongRow0Col, CellMark.X)
            var placed = 0
            while (true) {
                val pos = booster.pick(p, b) ?: break
                assertTrue(GameFixtures.isSolution(p, pos))
                assertFalse("already a cat", b[pos.row, pos.col] == CellMark.CAT)
                b = b.withMark(pos.row, pos.col, CellMark.CAT)
                placed++
                assertTrue(placed <= p.size)
            }
            assertEquals(p.size, placed)
            assertTrue(BoardValidator.analyse(p, b).solved)
        }
    }

    @Test
    fun neverReturnsAlreadyCorrectCatOnPartialBoards() {
        val rng = SeededRng(5L)
        val res = PuzzleGenerator.generateForLevel(14, 3L)
        if (res !is GenerationResult.Success) return
        val p = res.puzzle
        repeat(40) {
            var b = BoardState.empty(p.size)
            for (r in 0 until p.size) {
                if (rng.nextInt(2) == 0) b = b.withMark(r, p.solution[r], CellMark.CAT)
            }
            val pos = booster.pick(p, b)
            if (b.catCount == p.size) {
                assertNull(pos)
            } else {
                assertNotNull(pos)
                assertTrue(GameFixtures.isSolution(p, pos!!))
                assertFalse(b[pos.row, pos.col] == CellMark.CAT)
            }
        }
    }
}
