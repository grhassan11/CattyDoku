package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.engine.GameFixtures
import com.threetech.catpuzzle.domain.engine.GenStats
import com.threetech.catpuzzle.domain.engine.GenerationResult
import com.threetech.catpuzzle.domain.engine.SeededRng
import com.threetech.catpuzzle.domain.model.Puzzle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StartLevelTest {
    private val puzzle: Puzzle = GameFixtures.unique4()
    private val stats = GenStats(1, 0, 0L, 0, 0, false)

    private class FakeGenerator(
        private val failFirst: Int,
        private val ok: GenerationResult,
    ) : LevelGenerator {
        val seeds = ArrayList<Long>()
        val levels = ArrayList<Int>()

        override fun generate(level: Int, seed: Long): GenerationResult {
            seeds.add(seed)
            levels.add(level)
            return if (seeds.size <= failFirst) GenerationResult.Failure("fail ${seeds.size}", 1, 1L) else ok
        }
    }

    @Test
    fun firstTrySuccessUsesBaseSeed() {
        val gen = FakeGenerator(0, GenerationResult.Success(puzzle, stats))
        val r = StartLevel(gen)(7, 123L)
        assertEquals(StartLevelResult.Generated(puzzle, 0), r)
        assertEquals(listOf(123L), gen.seeds)
        assertEquals(listOf(7), gen.levels)
    }

    @Test
    fun retriesWithDerivedSeedsThenSucceeds() {
        val gen = FakeGenerator(2, GenerationResult.Success(puzzle, stats))
        val r = StartLevel(gen)(3, 99L)
        assertEquals(StartLevelResult.Generated(puzzle, 2), r)
        assertEquals(listOf(99L, SeededRng.mix(99L, 1L), SeededRng.mix(99L, 2L)), gen.seeds)
    }

    @Test
    fun derivedSeedsAreDistinct() {
        val gen = FakeGenerator(100, GenerationResult.Success(puzzle, stats))
        StartLevel(gen)(3, 99L)
        assertEquals(StartLevel.DEFAULT_MAX_ATTEMPTS, gen.seeds.toSet().size)
    }

    @Test
    fun givesUpAfterMaxAttemptsWithoutFallback() {
        val gen = FakeGenerator(100, GenerationResult.Success(puzzle, stats))
        val r = StartLevel(gen, maxAttempts = 4)(3, 1L)
        assertEquals(4, gen.seeds.size)
        assertTrue(r is StartLevelResult.Failed)
        r as StartLevelResult.Failed
        assertEquals(4, r.attempts)
        assertEquals("fail 4", r.reason)
    }

    @Test
    fun usesFallbackWhenAllAttemptsFail() {
        val gen = FakeGenerator(100, GenerationResult.Success(puzzle, stats))
        val fb = FallbackPuzzleProvider { puzzle }
        val r = StartLevel(gen, fb)(3, 1L)
        assertEquals(StartLevel.DEFAULT_MAX_ATTEMPTS, gen.seeds.size)
        assertTrue(r is StartLevelResult.FellBack)
        assertSame(puzzle, (r as StartLevelResult.FellBack).puzzle)
    }

    @Test
    fun deterministicAcrossRuns() {
        val a = FakeGenerator(3, GenerationResult.Success(puzzle, stats))
        val b = FakeGenerator(3, GenerationResult.Success(puzzle, stats))
        StartLevel(a)(9, 55L)
        StartLevel(b)(9, 55L)
        assertEquals(a.seeds, b.seeds)
    }
}
