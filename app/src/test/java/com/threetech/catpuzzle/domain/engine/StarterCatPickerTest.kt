package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.Puzzle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class StarterCatPickerTest {
    private val picker = StarterCatPicker()

    private fun generated(level: Int): Puzzle? {
        for (seed in 1L..4L) {
            val res = PuzzleGenerator.generateForLevel(level, seed)
            if (res is GenerationResult.Success) return res.puzzle
        }
        return null
    }

    /** Pick is a solution cell, deterministic, lowest technique over all solution cells, and finishable. */
    private fun assertPickIsSound(p: Puzzle) {
        val n = p.size
        val pos = picker.pick(p)
        assertNotNull(pos)
        pos!!
        assertEquals("not a solution cell", p.solution[pos.row], pos.col)
        assertEquals("not deterministic", pos, picker.pick(p))
        assertEquals("not deterministic (fresh instance)", pos, StarterCatPicker().pick(p))

        val idx = pos.row * n + pos.col
        val chosen = LogicSolver.solve(n, p.regions, idx)
        assertTrue("starter must leave a solvable puzzle", chosen.solved)
        assertArrayEquals(p.solution, chosen.cats)
        val minLevel = (0 until n).minOf { r ->
            val res = LogicSolver.solve(n, p.regions, r * n + p.solution[r])
            if (res.solved) res.maxLevel else Technique.MAX + 1
        }
        assertEquals("not the lowest technique", minLevel, chosen.maxLevel)
    }

    @Test
    fun unique4() = assertPickIsSound(GameFixtures.unique4())

    @Test
    fun confine4() = assertPickIsSound(GameFixtures.confine4())

    @Test
    fun crowd4() = assertPickIsSound(GameFixtures.crowd4())

    @Test
    fun generated5x5() {
        val p = generated(6)
        assumeTrue("generation failed", p != null)
        assertPickIsSound(p!!)
    }

    @Test
    fun generated6x6() {
        val p = generated(14)
        assumeTrue("generation failed", p != null)
        assertPickIsSound(p!!)
    }

    @Test
    fun generated7x7() {
        val p = generated(26)
        assumeTrue("generation failed", p != null)
        assertPickIsSound(p!!)
    }

    @Test
    fun naiveSolveWithoutPreplacedIsUnchanged() {
        val p = GameFixtures.unique4()
        val a = LogicSolver.solve(4, p.regions)
        val b = LogicSolver.solve(4, p.regions, -1)
        assertEquals(a.maxLevel, b.maxLevel)
        assertEquals(a.steps, b.steps)
    }

    @Test
    fun preplacedCatIsNotCountedAsAStep() {
        val p = GameFixtures.unique4()
        val r = LogicSolver.solve(4, p.regions, 0 * 4 + p.solution[0])
        assertTrue(r.solved)
        assertEquals(3, r.steps)
    }
}
