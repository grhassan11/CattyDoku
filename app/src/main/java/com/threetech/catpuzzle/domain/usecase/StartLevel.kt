package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.engine.DifficultyCurve
import com.threetech.catpuzzle.domain.engine.GenerationResult
import com.threetech.catpuzzle.domain.engine.GeneratorConfig
import com.threetech.catpuzzle.domain.engine.PuzzleGenerator
import com.threetech.catpuzzle.domain.engine.SeededRng
import com.threetech.catpuzzle.domain.model.Puzzle

/** Seam over [PuzzleGenerator] so tests can inject failures. */
fun interface LevelGenerator {
    fun generate(level: Int, seed: Long): GenerationResult
}

/** Production generator: the deterministic engine with a fixed config. */
class EnginePuzzleGenerator(private val config: GeneratorConfig = GeneratorConfig.DEFAULT) : LevelGenerator {
    override fun generate(level: Int, seed: Long): GenerationResult {
        val primary = PuzzleGenerator.generateForLevel(level, seed, config)
        if (primary is GenerationResult.Success) return primary
        // 8x8/9x9 only (empty list for <= 7x7): same seed mix, smaller board / lower target, so a
        // slow or failing big board can never dead-end the level.
        val levelSeed = SeededRng.mix(seed, level.coerceAtLeast(1).toLong())
        for (spec in DifficultyCurve.fallbackSpecsFor(level)) {
            val r = PuzzleGenerator.generate(spec, levelSeed, config)
            if (r is GenerationResult.Success) return r
        }
        return primary
    }
}

/** Last-resort puzzle source when generation keeps failing (e.g. bundled puzzles, added later). */
fun interface FallbackPuzzleProvider {
    fun puzzleFor(level: Int): Puzzle?

    companion object {
        val NONE = FallbackPuzzleProvider { null }
    }
}

sealed interface StartLevelResult {
    /** [attempt] is 0-based: 0 = the base seed worked first time. */
    data class Generated(val puzzle: Puzzle, val attempt: Int) : StartLevelResult

    data class FellBack(val puzzle: Puzzle) : StartLevelResult

    data class Failed(val reason: String, val attempts: Int) : StartLevelResult
}

/**
 * Build the puzzle for a level. Attempt 0 uses [baseSeed]; each retry uses a derived seed
 * (SeededRng.mix(baseSeed, attempt)). After [maxAttempts] failures asks [fallback], else Failed.
 * Clock-free and deterministic: same inputs => same result.
 */
class StartLevel(
    private val generator: LevelGenerator,
    private val fallback: FallbackPuzzleProvider = FallbackPuzzleProvider.NONE,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts" }
    }

    operator fun invoke(level: Int, baseSeed: Long): StartLevelResult {
        var lastReason = "no attempt"
        for (attempt in 0 until maxAttempts) {
            val seed = if (attempt == 0) baseSeed else SeededRng.mix(baseSeed, attempt.toLong())
            when (val r = generator.generate(level, seed)) {
                is GenerationResult.Success -> return StartLevelResult.Generated(r.puzzle, attempt)
                is GenerationResult.Failure -> lastReason = r.reason
            }
        }
        val fb = fallback.puzzleFor(level)
        return if (fb != null) StartLevelResult.FellBack(fb) else StartLevelResult.Failed(lastReason, maxAttempts)
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 5
    }
}
