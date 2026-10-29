package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.engine.GENERATOR_VERSION
import com.threetech.catpuzzle.domain.engine.GameFixtures
import com.threetech.catpuzzle.domain.engine.GenStats
import com.threetech.catpuzzle.domain.engine.GenerationResult
import com.threetech.catpuzzle.domain.model.Puzzle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GetOrCreatePuzzleTest {
    private val stats = GenStats(1, 0, 0L, 0, 0, false)
    private val generated: Puzzle = GameFixtures.puzzle(4, GameFixtures.unique4().regions, GameFixtures.unique4().solution, level = 5)

    private class CountingGenerator(private val result: GenerationResult) : LevelGenerator {
        var calls = 0
        val seeds = ArrayList<Long>()

        override fun generate(level: Int, seed: Long): GenerationResult {
            calls++
            seeds.add(seed)
            return result
        }
    }

    private fun failing() = GenerationResult.Failure("nope", 1, 1L)

    @Test
    fun cacheHitSkipsGenerator() = runTest {
        val cache = FakePuzzleRepository()
        val cached = GameFixtures.puzzle(4, generated.regions, generated.solution, level = 5)
        cache.putIfAbsent(cached)
        val gen = CountingGenerator(GenerationResult.Success(generated, stats))

        val r = GetOrCreatePuzzle(cache, StartLevel(gen))(5)

        assertTrue(r is PuzzleLoadResult.Cached)
        assertSame(cached, (r as PuzzleLoadResult.Cached).puzzle)
        assertEquals(0, gen.calls)
    }

    @Test
    fun cachedPuzzleWinsOverNewGeneratorVersion() = runTest {
        val cache = FakePuzzleRepository()
        val old = Puzzle(4, generated.regions, generated.solution, 9L, 5, 1, GENERATOR_VERSION - 1)
        cache.putIfAbsent(old)
        val gen = CountingGenerator(GenerationResult.Success(generated, stats))

        val r = GetOrCreatePuzzle(cache, StartLevel(gen))(5) as PuzzleLoadResult.Cached

        assertEquals(GENERATOR_VERSION - 1, r.puzzle.generatorVersion)
        assertEquals(0, gen.calls)
        assertEquals(1, cache.rows.size)
    }

    @Test
    fun cacheMissGeneratesStoresAndNextCallHitsCache() = runTest {
        val cache = FakePuzzleRepository()
        val gen = CountingGenerator(GenerationResult.Success(generated, stats))
        val useCase = GetOrCreatePuzzle(cache, StartLevel(gen), baseSeed = 77L)

        val first = useCase(5)
        assertEquals(PuzzleLoadResult.Created(generated, fellBack = false), first)
        assertEquals(listOf(77L), gen.seeds)
        assertSame(generated, cache.rows[5])

        val second = useCase(5)
        assertTrue(second is PuzzleLoadResult.Cached)
        assertEquals(1, gen.calls)
    }

    @Test
    fun fallbackPuzzleIsCachedToo() = runTest {
        val cache = FakePuzzleRepository()
        val gen = CountingGenerator(failing())
        val useCase = GetOrCreatePuzzle(cache, StartLevel(gen, FallbackPuzzleProvider { generated }, maxAttempts = 2))

        val r = useCase(5)

        assertEquals(PuzzleLoadResult.Created(generated, fellBack = true), r)
        assertSame(generated, cache.rows[5])
        assertEquals(2, gen.calls)
    }

    @Test
    fun totalFailureReportsFailedAndCachesNothing() = runTest {
        val cache = FakePuzzleRepository()
        val gen = CountingGenerator(failing())

        val r = GetOrCreatePuzzle(cache, StartLevel(gen, maxAttempts = 2))(5)

        assertEquals(PuzzleLoadResult.Failed("nope"), r)
        assertTrue(cache.rows.isEmpty())
        assertEquals(0, cache.putAttempts)
    }

    @Test
    fun lostPutRaceReturnsTheStoredPuzzle() = runTest {
        val winner = Puzzle(4, generated.regions, generated.solution, 1234L, 5, 1, GENERATOR_VERSION)
        val cache = object : PuzzleRepositoryRace(winner) {}
        val gen = CountingGenerator(GenerationResult.Success(generated, stats))

        val r = GetOrCreatePuzzle(cache, StartLevel(gen))(5)

        assertTrue(r is PuzzleLoadResult.Cached)
        assertSame(winner, (r as PuzzleLoadResult.Cached).puzzle)
    }

    /** First get() misses, then a concurrent writer inserts [winner] before our put. */
    private open class PuzzleRepositoryRace(private val winner: Puzzle) :
        com.threetech.catpuzzle.domain.repository.PuzzleRepository {
        private var stored: Puzzle? = null
        private var firstGet = true

        override suspend fun get(levelIndex: Int): Puzzle? {
            if (firstGet) {
                firstGet = false
                return null
            }
            return stored
        }

        override suspend fun putIfAbsent(puzzle: Puzzle): Boolean {
            stored = winner
            return false
        }
    }
}
