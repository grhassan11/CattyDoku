package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.LevelSpec
import com.threetech.catpuzzle.domain.model.Puzzle

/**
 * TEMP 9x9 sample: debug-only 9x9 puzzle for feel review. Delete this file with the other "TEMP 9x9 sample" markers.
 * Uses the real [PuzzleGenerator] (unique + logic-solvable guarantee). Does NOT touch [DifficultyCurve] or GENERATOR_VERSION.
 * Pure Kotlin, blocking: call on Dispatchers.Default. Deterministic: fixed seeds.
 */
object Sample9x9 {
    /** Level index of the sample (>= mouse unlock level so all 3 boosters show). Never persisted. */
    const val LEVEL_INDEX = 99
    const val FIXED_HINTS = 5
    const val FIXED_CATS = 5
    const val FIXED_MOUSE = 3

    private const val BASE_SEED = 0x39583953L
    private const val MIN_REGION = 2
    private const val BALANCE = 40
    private const val ATTEMPTS_PER_TARGET = 6

    /** Smaller budgets than the real generator so one try is short; several tries instead (abortable between tries). */
    private val CONFIG = GeneratorConfig(
        stageAttempts = listOf(15, 15, 20, 30),
        maxRepairsPerAttempt = 60,
        nodeCapPerCount = 100_000,
        totalNodeBudget = 3_000_000L,
    )

    /** Target technique 3 first, then easier. Null if every try fails. [checkCancelled] throws to abort. */
    fun generate(checkCancelled: () -> Unit = {}): Puzzle? {
        for (target in intArrayOf(3, 2, 1)) {
            val spec = LevelSpec(
                levelIndex = LEVEL_INDEX,
                size = 9,
                targetTechnique = target,
                minRegionSize = MIN_REGION,
                balancePercent = BALANCE,
                minOpeningSingles = 0,
            )
            for (attempt in 0 until ATTEMPTS_PER_TARGET) {
                checkCancelled()
                val seed = SeededRng.mix(BASE_SEED + target, attempt.toLong())
                val r = PuzzleGenerator.generate(spec, seed, CONFIG)
                if (r is GenerationResult.Success) return r.puzzle
            }
        }
        return null
    }
}
