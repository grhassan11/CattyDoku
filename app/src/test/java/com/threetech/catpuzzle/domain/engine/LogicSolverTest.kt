package com.threetech.catpuzzle.domain.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicSolverTest {
    @Test
    fun nakedSinglesOnly() {
        val r = LogicSolver.solve(4, Fixtures.UNIQUE_4)
        assertTrue(r.solved)
        assertEquals(Technique.NAKED_SINGLE, r.maxLevel)
        assertEquals(4, r.openingSingles)
        assertEquals(4, r.levelCounts[Technique.NAKED_SINGLE])
        assertArrayEquals(Fixtures.UNIQUE_4_SOLUTION, r.cats)
    }

    @Test
    fun confinementFirstStep() {
        val r = LogicSolver.solve(4, Fixtures.CONFINE_4)
        assertEquals(0, r.openingSingles)
        assertTrue(r.levelCounts[Technique.CONFINEMENT] >= 1)
        assertTrue(r.maxLevel >= Technique.CONFINEMENT)
    }

    @Test
    fun crowdingPairFirstStep() {
        val r = LogicSolver.solve(4, Fixtures.CROWD_4)
        assertEquals(0, r.openingSingles)
        assertTrue(r.maxLevel >= Technique.CROWDING_PAIR)
        assertTrue(r.levelCounts[Technique.CROWDING_PAIR] >= 1)
    }

    @Test
    fun multiSolutionBoardGetsStuckNeverWrong() {
        val r = LogicSolver.solve(4, Fixtures.MULTI_4)
        assertFalse(r.solved)
        assertFalse(r.contradiction)
        assertTrue(r.stuck)
    }

    @Test
    fun impossibleBoardReportsContradictionOrStuckNotSolved() {
        val r = LogicSolver.solve(4, IntArray(16))
        assertFalse(r.solved)
    }

    @Test
    fun stepsEqualSumOfLevelCounts() {
        val r = LogicSolver.solve(4, Fixtures.UNIQUE_4)
        assertEquals(r.steps, r.levelCounts.sum())
    }

    @Test
    fun solverAgreesWithCounterOnGeneratedBoards() {
        for (seed in 1L..6L) {
            val res = PuzzleGenerator.generateForLevel(8, seed)
            if (res !is GenerationResult.Success) continue
            val p = res.puzzle
            val lr = LogicSolver.solve(p.size, p.regions)
            assertTrue(lr.solved)
            assertArrayEquals(p.solution, lr.cats)
        }
    }
}
