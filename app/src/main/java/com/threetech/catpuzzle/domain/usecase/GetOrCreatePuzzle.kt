package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.model.Puzzle
import com.threetech.catpuzzle.domain.repository.PuzzleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface PuzzleLoadResult {
    /** Served from the cache; generator was not touched (even if [Puzzle.generatorVersion] is old). */
    data class Cached(val puzzle: Puzzle) : PuzzleLoadResult

    /** Newly produced by [StartLevel] and stored. [fellBack] = came from the fallback provider. */
    data class Created(val puzzle: Puzzle, val fellBack: Boolean) : PuzzleLoadResult

    data class Failed(val reason: String) : PuzzleLoadResult
}

/**
 * Cache-first puzzle source. A cached puzzle ALWAYS wins over generation, so a started or completed
 * level never changes when GENERATOR_VERSION is bumped. [baseSeed] is the app-wide seed that
 * PuzzleGenerator mixes with the level index.
 */
class GetOrCreatePuzzle(
    private val cache: PuzzleRepository,
    private val startLevel: StartLevel,
    private val baseSeed: Long = DEFAULT_BASE_SEED,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(levelIndex: Int): PuzzleLoadResult {
        cache.get(levelIndex)?.let { return PuzzleLoadResult.Cached(it) }

        val created: Puzzle
        val fellBack: Boolean
        when (val r = withContext(dispatcher) { startLevel(levelIndex, baseSeed) }) {
            is StartLevelResult.Generated -> {
                created = r.puzzle
                fellBack = false
            }
            is StartLevelResult.FellBack -> {
                created = r.puzzle
                fellBack = true
            }
            is StartLevelResult.Failed -> return PuzzleLoadResult.Failed(r.reason)
        }

        if (!cache.putIfAbsent(created)) {
            // Lost a race with another writer: the stored one wins.
            cache.get(levelIndex)?.let { return PuzzleLoadResult.Cached(it) }
        }
        return PuzzleLoadResult.Created(created, fellBack)
    }

    companion object {
        /** "MEOW". Changing it changes every future level: treat like a generator version bump. */
        const val DEFAULT_BASE_SEED: Long = 0x4D454F57L
    }
}
