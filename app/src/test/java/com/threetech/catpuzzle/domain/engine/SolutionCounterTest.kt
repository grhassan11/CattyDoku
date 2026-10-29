package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.engine.SolutionCounter.Outcome
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolutionCounterTest {
    @Test
    fun uniqueBoard() {
        val r = SolutionCounter.count(4, Fixtures.UNIQUE_4)
        assertEquals(Outcome.UNIQUE, r.outcome)
        assertEquals(1, r.solutionCount)
        assertArrayEquals(Fixtures.UNIQUE_4_SOLUTION, r.solutions[0])
    }

    @Test
    fun multiBoardCapTwo() {
        val r = SolutionCounter.count(4, Fixtures.MULTI_4)
        assertEquals(Outcome.MULTI, r.outcome)
        assertEquals(2, r.solutionCount)
        assertEquals(2, r.solutions.size)
    }

    @Test
    fun abortedWhenNodeCapTiny() {
        val r = SolutionCounter.count(4, Fixtures.MULTI_4, 2, 1)
        assertEquals(Outcome.ABORTED, r.outcome)
        assertTrue(r.nodes <= 2)
    }

    @Test
    fun noneWhenRegionMissing() {
        // all cells region 0 => regions 1..3 empty => impossible
        val r = SolutionCounter.count(4, IntArray(16))
        assertEquals(Outcome.NONE, r.outcome)
        assertEquals(0, r.solutionCount)
    }

    @Test
    fun nodeCountDeterministic() {
        val a = SolutionCounter.count(4, Fixtures.MULTI_4)
        val b = SolutionCounter.count(4, Fixtures.MULTI_4)
        assertEquals(a.nodes, b.nodes)
    }
}
