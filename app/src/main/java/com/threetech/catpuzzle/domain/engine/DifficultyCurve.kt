package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.LevelSpec

/**
 * Level -> LevelSpec. Every tunable lives in this object.
 *
 * 4x4 L1-4, 5x5 L5-12, 6x6 L13-24, 7x7 L25+ (sawtooth cycle).
 * Targets are Technique levels (1 naked single .. 5 look-ahead).
 * Changing any table changes generated puzzles => bump GENERATOR_VERSION.
 */
object DifficultyCurve {
    // ---- tunables ----
    const val LAST_LEVEL_4X4 = 4
    const val LAST_LEVEL_5X5 = 12
    const val LAST_LEVEL_6X6 = 24

    private val TARGETS_4X4 = intArrayOf(1, 1, 2, 2)
    private val TARGETS_5X5 = intArrayOf(2, 2, 2, 3, 3, 3, 4, 4)
    private val TARGETS_6X6 = intArrayOf(3, 3, 4, 3, 4, 4, 5, 4, 4, 5, 4, 5)

    /** 7x7+ repeating sawtooth: easy, medium, medium, hard, hard, hardest, then drop. */
    private val CYCLE_7X7 = intArrayOf(2, 3, 3, 4, 4, 5)

    private const val BALANCE_4X4 = 70
    private const val BALANCE_5X5 = 60
    private const val BALANCE_6X6 = 50
    private const val BALANCE_7X7 = 40

    private const val MIN_REGION = 2

    fun sizeFor(level: Int): Int = when {
        level <= LAST_LEVEL_4X4 -> 4
        level <= LAST_LEVEL_5X5 -> 5
        level <= LAST_LEVEL_6X6 -> 6
        else -> 7
    }

    fun specFor(level: Int): LevelSpec {
        val lvl = level.coerceAtLeast(1)
        return when (sizeFor(lvl)) {
            4 -> {
                val t = TARGETS_4X4[lvl - 1]
                // target 1 needs a 1-cell region (otherwise no starting naked single exists)
                LevelSpec(lvl, 4, t, if (t == 1) 1 else MIN_REGION, BALANCE_4X4, if (t == 1) 1 else 0)
            }
            5 -> LevelSpec(
                lvl, 5, TARGETS_5X5[lvl - LAST_LEVEL_4X4 - 1], MIN_REGION, BALANCE_5X5, 0,
            )
            6 -> LevelSpec(
                lvl, 6, TARGETS_6X6[lvl - LAST_LEVEL_5X5 - 1], MIN_REGION, BALANCE_6X6, 0,
            )
            else -> LevelSpec(
                lvl, 7, CYCLE_7X7[(lvl - LAST_LEVEL_6X6 - 1) % CYCLE_7X7.size], MIN_REGION, BALANCE_7X7, 0,
            )
        }
    }
}
