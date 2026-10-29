package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.LevelSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleGeneratorTest {

    private fun gen(level: Int, seed: Long): GenerationResult =
        PuzzleGenerator.generate(DifficultyCurve.specFor(level), seed)

    /** Levels spanning one size band; seed picks the level so techniques vary. */
    private fun levelFor(size: Int, seed: Long): Int = when (size) {
        4 -> 1 + (seed % 4).toInt()
        5 -> 5 + (seed % 8).toInt()
        6 -> 13 + (seed % 12).toInt()
        else -> 25 + (seed % 6).toInt()
    }

    private fun sweep(size: Int, seeds: Int) {
        var ok = 0
        val cfg = GeneratorConfig.DEFAULT
        val attemptCap = (0..5).sumOf { cfg.attemptsFor(it) }
        for (s in 1..seeds) {
            val res = gen(levelFor(size, s.toLong()), s.toLong() * 7919L)
            when (res) {
                is GenerationResult.Success -> {
                    ok++
                    val err = Fixtures.checkPuzzle(res.puzzle)
                    assertNull("size $size seed $s: $err ${res.puzzle}", err)
                    assertEquals(size, res.puzzle.size)
                    assertTrue(res.stats.attempts <= attemptCap)
                    assertTrue(res.stats.solverNodes <= cfg.totalNodeBudget)
                }
                is GenerationResult.Failure -> {
                    assertTrue(res.attempts <= attemptCap)
                    assertTrue(res.solverNodes <= cfg.totalNodeBudget + cfg.nodeCapPerCount)
                }
            }
        }
        assertTrue("size $size success $ok/$seeds", ok * 10 >= seeds * 8)
    }

    @Test fun uniqueAndLogicSolvable4() = sweep(4, 40)
    @Test fun uniqueAndLogicSolvable5() = sweep(5, 40)
    @Test fun uniqueAndLogicSolvable6() = sweep(6, 24)
    @Test fun uniqueAndLogicSolvable7() = sweep(7, 12)

    @Test
    fun deterministicSameSeedTwice() {
        for (level in listOf(1, 3, 6, 10, 14, 20, 25, 28)) {
            val a = gen(level, 424242L)
            val b = gen(level, 424242L)
            when (a) {
                is GenerationResult.Success -> {
                    assertTrue(b is GenerationResult.Success)
                    assertEquals(a.puzzle, (b as GenerationResult.Success).puzzle)
                    assertEquals(a.stats.attempts, b.stats.attempts)
                    assertEquals(a.stats.solverNodes, b.stats.solverNodes)
                }
                is GenerationResult.Failure -> {
                    assertTrue(b is GenerationResult.Failure)
                    assertEquals(a.reason, (b as GenerationResult.Failure).reason)
                }
            }
        }
    }

    @Test
    fun generateForLevelDeterministic() {
        val a = PuzzleGenerator.generateForLevel(9, 1L)
        val b = PuzzleGenerator.generateForLevel(9, 1L)
        if (a is GenerationResult.Success && b is GenerationResult.Success) {
            assertEquals(a.puzzle, b.puzzle)
            assertEquals(9, a.puzzle.levelIndex)
        } else {
            assertTrue(a is GenerationResult.Failure && b is GenerationResult.Failure)
        }
    }

    @Test
    fun differentSeedsUsuallyDiffer() {
        val keys = HashSet<String>()
        for (s in 1L..10L) {
            val r = gen(9, s)
            if (r is GenerationResult.Success) keys.add(Fixtures.canonicalKey(r.puzzle))
        }
        assertTrue("distinct ${keys.size}", keys.size >= 5)
    }

    @Test
    fun zeroBudgetReturnsFailureNotPuzzle() {
        val cfg = GeneratorConfig(stageAttempts = listOf(0))
        val r = PuzzleGenerator.generate(DifficultyCurve.specFor(5), 1L, cfg)
        assertTrue(r is GenerationResult.Failure)
    }

    @Test
    fun unsupportedSizeFails() {
        val spec = LevelSpec(1, 3, 1, 1, 50, 0)
        assertTrue(PuzzleGenerator.generate(spec, 1L) is GenerationResult.Failure)
        val big = LevelSpec(1, 10, 1, 1, 50, 0)
        assertTrue(PuzzleGenerator.generate(big, 1L) is GenerationResult.Failure)
    }

    @Test
    fun tinyNodeBudgetFailsInsteadOfBadBoard() {
        val cfg = GeneratorConfig(totalNodeBudget = 5L)
        val r = PuzzleGenerator.generate(DifficultyCurve.specFor(8), 3L, cfg)
        if (r is GenerationResult.Success) {
            assertNull(Fixtures.checkPuzzle(r.puzzle))
        } else {
            assertTrue(r is GenerationResult.Failure)
        }
    }

    @Test
    fun variety4x4() {
        val keys = HashSet<String>()
        var ok = 0
        for (s in 1..120) {
            val r = gen(levelFor(4, s.toLong()), s.toLong())
            if (r is GenerationResult.Success) {
                ok++
                keys.add(Fixtures.canonicalKey(r.puzzle))
            }
        }
        println("VARIETY 4x4: ${keys.size} distinct boards from $ok successes over 120 seeds")
        assertTrue("4x4 distinct ${keys.size}", keys.size >= 8)
    }

    @Test
    fun variety5x5() {
        val keys = HashSet<String>()
        var ok = 0
        for (s in 1..100) {
            val r = gen(levelFor(5, s.toLong()), s.toLong())
            if (r is GenerationResult.Success) {
                ok++
                keys.add(Fixtures.canonicalKey(r.puzzle))
            }
        }
        println("VARIETY 5x5: ${keys.size} distinct boards from $ok successes over 100 seeds")
        assertTrue("5x5 distinct ${keys.size}", keys.size >= 40)
    }
}
