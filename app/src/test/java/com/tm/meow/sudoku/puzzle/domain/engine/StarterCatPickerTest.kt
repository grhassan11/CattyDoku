package com.tm.meow.sudoku.puzzle.domain.engine

import com.tm.meow.sudoku.puzzle.domain.model.Puzzle
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
        val sizes = IntArray(n).also { s -> p.regions.forEach { s[it]++ } }
        val rows = (0 until n).filter { sizes[p.regions[it * n + p.solution[it]]] > 1 }
            .ifEmpty { (0 until n).toList() }
        assertTrue("starter sits in a 1-cell region", rows.contains(pos.row))
        val minLevel = rows.minOf { r ->
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
    fun levels1to10HaveSingletonAndStarterAvoidsIt() {
        var checked = 0
        for (level in 1..10) {
            for (seed in 1L..8L) {
                val res = PuzzleGenerator.generateForLevel(level, seed) as? GenerationResult.Success ?: continue
                val p = res.puzzle
                val sizes = IntArray(p.size).also { s -> p.regions.forEach { s[it]++ } }
                assertTrue("L$level seed $seed: no 1-cell region", sizes.any { it == 1 })
                val pos = picker.pick(p)!!
                assertTrue("L$level seed $seed: starter in 1-cell region", sizes[p.regions[pos.row * p.size + pos.col]] > 1)
                checked++
            }
        }
        assertTrue("checked $checked", checked >= 60)
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
