package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.LevelSpec

/**
 * Level -> LevelSpec. Every tunable lives in this object.
 *
 * 4x4 L1-4, 5x5 L5-12, 6x6 L13-24, then from L25 a repeating 6-level sawtooth where size and
 * technique target rise together: 7,7,8,8,9,9 with targets 2,3,3,4,4,5 (index = (lvl - 25) % 6).
 * Board never exceeds 9x9 ([MAX_BOARD_SIZE]).
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

    /** L25+ repeating sawtooth, parallel arrays (same length): easy, medium, medium, hard, hard, hardest, then drop. */
    private val CYCLE_TARGETS = intArrayOf(2, 3, 3, 4, 4, 5)
    private val CYCLE_SIZES = intArrayOf(7, 7, 8, 8, 9, 9)

    /** Smallest size on the mixed cycle; also the safe-fallback size for 8x8/9x9 generation failures. */
    const val MIN_CYCLE_SIZE = 7

    private const val BALANCE_4X4 = 70
    private const val BALANCE_5X5 = 60
    private const val BALANCE_6X6 = 50
    private const val BALANCE_7X7 = 40
    private const val BALANCE_8X8 = 35
    private const val BALANCE_9X9 = 30

    private const val MIN_REGION = 2

    init {
        require(CYCLE_TARGETS.size == CYCLE_SIZES.size) { "cycle arrays differ" }
        require(CYCLE_SIZES.all { it in MIN_CYCLE_SIZE..MAX_BOARD_SIZE }) { "cycle size out of range" }
    }

    private fun cycleIndex(level: Int): Int = (level - LAST_LEVEL_6X6 - 1) % CYCLE_SIZES.size

    private fun balanceFor(size: Int): Int = when (size) {
        4 -> BALANCE_4X4
        5 -> BALANCE_5X5
        6 -> BALANCE_6X6
        7 -> BALANCE_7X7
        8 -> BALANCE_8X8
        else -> BALANCE_9X9
    }

    fun sizeFor(level: Int): Int = when {
        level <= LAST_LEVEL_4X4 -> 4
        level <= LAST_LEVEL_5X5 -> 5
        level <= LAST_LEVEL_6X6 -> 6
        else -> CYCLE_SIZES[cycleIndex(level)]
    }

    fun specFor(level: Int): LevelSpec {
        val lvl = level.coerceAtLeast(1)
        val size = sizeFor(lvl)
        return when (size) {
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
                lvl, size, CYCLE_TARGETS[cycleIndex(lvl)], MIN_REGION, balanceFor(size), 0,
            )
        }
    }

    /**
     * Deterministic safe fallbacks, tried in order after [specFor] fails (budgets exhausted) for
     * 8x8/9x9 levels: next smaller size(s) down to [MIN_CYCLE_SIZE] at the same target, then
     * [MIN_CYCLE_SIZE] with target - 1. Empty for sizes <= 7 (their behavior is unchanged).
     */
    fun fallbackSpecsFor(level: Int): List<LevelSpec> {
        val base = specFor(level)
        if (base.size <= MIN_CYCLE_SIZE) return emptyList()
        val out = ArrayList<LevelSpec>()
        for (s in base.size - 1 downTo MIN_CYCLE_SIZE) {
            out.add(base.copy(size = s, balancePercent = balanceFor(s)))
        }
        val t = (base.targetTechnique - 1).coerceAtLeast(1)
        out.add(base.copy(size = MIN_CYCLE_SIZE, targetTechnique = t, balancePercent = BALANCE_7X7))
        return out
    }
}
