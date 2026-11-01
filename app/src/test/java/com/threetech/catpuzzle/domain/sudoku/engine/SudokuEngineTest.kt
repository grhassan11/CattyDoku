package com.threetech.catpuzzle.domain.sudoku.engine

import com.threetech.catpuzzle.domain.engine.SeededRng
import com.threetech.catpuzzle.domain.sudoku.model.SudokuGeometry
import com.threetech.catpuzzle.domain.sudoku.model.SudokuSize
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTechnique
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTier
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuEngineTest {
    private fun digits(s: String) = IntArray(s.length) { s[it] - '0' }

    private val classic = digits("530070000600195000098000060800060003400803001700020006060000280000419005000080079")
    private val classicSol = digits("534678912672195348198342567859761423426853791713924856961537284287419635345286179")

    @Test
    fun geometryPeers() {
        assertEquals(7, SudokuGeometry.of(SudokuSize.S4).peers[0].size)
        assertEquals(12, SudokuGeometry.of(SudokuSize.S6).peers[0].size)
        assertEquals(20, SudokuGeometry.of(SudokuSize.S9).peers[40].size)
        val g6 = SudokuGeometry.of(SudokuSize.S6)
        assertEquals(18, g6.units.size)
        assertTrue(g6.units.all { it.size == 6 })
        // 6x6 box = 2 rows x 3 cols: cells 0..2 and 6..8 share box 0, cell 3 is box 1, cell 12 is box 2
        assertEquals(0, g6.boxOf[8]); assertEquals(1, g6.boxOf[3]); assertEquals(2, g6.boxOf[12])
    }

    @Test
    fun solverSolvesClassic() {
        assertEquals(1, SudokuSolver.countSolutions(SudokuSize.S9, classic))
        assertArrayEquals(classicSol, SudokuSolver.solve(SudokuSize.S9, classic))
    }

    @Test
    fun countCappedAndInvalid() {
        assertEquals(2, SudokuSolver.countSolutions(SudokuSize.S4, IntArray(16), 2))
        val dup = classic.copyOf().also { it[2] = 5 } // 5 twice in row 0
        assertEquals(0, SudokuSolver.countSolutions(SudokuSize.S9, dup))
        assertNull(SudokuSolver.solve(SudokuSize.S9, dup))
    }

    @Test
    fun randomFullGridsAreValidAndSeeded() {
        for (size in SudokuSize.values()) {
            val a = SudokuSolver.solve(size, IntArray(size.cells), SeededRng(7))!!
            val b = SudokuSolver.solve(size, IntArray(size.cells), SeededRng(7))!!
            assertTrue(SudokuValidator.isValidSolution(size, a))
            assertArrayEquals(a, b)
        }
    }

    @Test
    fun gradeClassicIsSinglesOnly() {
        assertTrue(SudokuSolver.grade(SudokuSize.S9, classic) <= SudokuTechnique.HIDDEN_SINGLE)
        assertEquals(SudokuTechnique.GUESS, SudokuSolver.grade(SudokuSize.S4, IntArray(16)))
    }

    @Test
    fun generatorUniqueAndConsistentForEveryTier() {
        for (tier in SudokuTier.values()) for (seed in longArrayOf(1L, 2L)) {
            val p = SudokuGenerator.generate(tier, seed)
            assertEquals("$tier/$seed unique", 1, SudokuSolver.countSolutions(tier.size, p.givens, 2))
            assertTrue(SudokuValidator.isValidSolution(tier.size, p.solution))
            assertArrayEquals(p.solution, SudokuSolver.solve(tier.size, p.givens))
            for (c in p.givens.indices) if (p.givens[c] != 0) assertEquals(p.solution[c], p.givens[c])
            assertEquals(SUDOKU_GENERATOR_VERSION, p.generatorVersion)
            assertEquals(SudokuSolver.grade(tier.size, p.givens), p.rating)
            if (p.matchedTier) {
                assertTrue("$tier/$seed clues ${p.clues}", p.clues in tier.minClues..tier.maxClues)
                assertTrue("$tier/$seed rating ${p.rating}", p.rating in tier.minTechnique..tier.maxTechnique)
            }
        }
    }

    @Test
    fun easyMatchesTier() {
        for (seed in 1L..3L) assertTrue(SudokuGenerator.generate(SudokuTier.EASY, seed).matchedTier)
    }

    @Test
    fun generatorDeterministic() {
        for (tier in listOf(SudokuTier.EASY, SudokuTier.MEDIUM, SudokuTier.HARD)) {
            val a = SudokuGenerator.generate(tier, 99L)
            val b = SudokuGenerator.generate(tier, 99L)
            assertArrayEquals(a.givens, b.givens)
            assertArrayEquals(a.solution, b.solution)
            assertEquals(a.rating, b.rating)
            assertFalse(a.givens.contentEquals(SudokuGenerator.generate(tier, 100L).givens))
        }
    }

    @Test
    fun validatorConflictsAndSolved() {
        val v = classicSol.copyOf()
        assertTrue(SudokuValidator.conflicts(SudokuSize.S9, v).none { it })
        v[0] = v[1]
        val conf = SudokuValidator.conflicts(SudokuSize.S9, v)
        assertTrue(conf[0] && conf[1])
        assertTrue(SudokuValidator.isSolved(classicSol.copyOf(), classicSol))
        assertFalse(SudokuValidator.isSolved(v, classicSol))
    }

    @Test
    fun hintPrefersSingleAndNeverContradictsSolution() {
        val h = SudokuHintFinder.find(SudokuSize.S9, classicSol, classic, null)
        assertNotNull(h)
        assertEquals(classicSol[h!!.cell], h.digit)
        assertEquals(0, classic[h.cell])
        assertTrue(h.technique <= SudokuTechnique.HIDDEN_SINGLE)
        // selected wrong cell -> its correct digit
        val wrong = classic.copyOf().also { it[2] = 9 }
        val h2 = SudokuHintFinder.find(SudokuSize.S9, classicSol, wrong, 2)!!
        assertEquals(2, h2.cell); assertEquals(classicSol[2], h2.digit)
        // solved -> null
        assertNull(SudokuHintFinder.find(SudokuSize.S9, classicSol, classicSol, null))
    }

    @Test
    fun hintFallbackRevealsFromSolution() {
        val size = SudokuSize.S4
        val sol = SudokuSolver.solve(size, IntArray(16), SeededRng(3))!!
        val h = SudokuHintFinder.find(size, sol, IntArray(16), null)!!
        assertEquals(sol[h.cell], h.digit)
        assertEquals(SudokuTechnique.GUESS, h.technique)
    }

    @Test
    fun scoreCalculation() {
        val t = SudokuTier.HARD // x3, par 600 s
        assertEquals(30, SudokuScoreCalculator.digitPoints(t))
        assertEquals(30, SudokuScoreCalculator.afterCorrect(0, t))
        assertEquals(0, SudokuScoreCalculator.afterMistake(10))
        assertEquals(35, SudokuScoreCalculator.afterMistake(50))
        assertEquals(3 * 100, SudokuScoreCalculator.timeBonus(t, 500_000))
        assertEquals(0, SudokuScoreCalculator.timeBonus(t, 700_000))
        assertEquals(30 * 40 - 2 * 15 + 3 * 100, SudokuScoreCalculator.finalScore(t, 40, 2, 500_000))
        assertEquals(0 + 0, SudokuScoreCalculator.finalScore(t, 0, 3, 900_000))
    }

    @Test
    fun unlockRules() {
        assertTrue(SudokuTier.EASY.isUnlocked(emptyMap()))
        assertTrue(SudokuTier.MEDIUM.isUnlocked(emptyMap()))
        assertFalse(SudokuTier.HARD.isUnlocked(mapOf(SudokuTier.MEDIUM to 1)))
        assertTrue(SudokuTier.HARD.isUnlocked(mapOf(SudokuTier.MEDIUM to 2)))
        assertFalse(SudokuTier.EXPERT.isUnlocked(mapOf(SudokuTier.HARD to 3)))
        assertTrue(SudokuTier.EXPERT.isUnlocked(mapOf(SudokuTier.HARD to 4)))
        assertTrue(SudokuTier.MASTER.isUnlocked(mapOf(SudokuTier.EXPERT to 10)))
        assertFalse(SudokuTier.EXTREME.isUnlocked(mapOf(SudokuTier.MASTER to 13, SudokuTier.EXPERT to 99)))
        assertTrue(SudokuTier.EXTREME.isUnlocked(mapOf(SudokuTier.MASTER to 14)))
    }

    @Test
    fun tierTablesSane() {
        for (t in SudokuTier.values()) {
            assertTrue(t.minClues <= t.maxClues && t.maxClues < t.size.cells)
            assertTrue(t.minTechnique <= t.maxTechnique && t.maxTechnique < SudokuTechnique.GUESS)
        }
        assertEquals(listOf(1, 2, 3, 4, 5, 6), SudokuTier.values().map { it.scoreMultiplier })
    }
}
