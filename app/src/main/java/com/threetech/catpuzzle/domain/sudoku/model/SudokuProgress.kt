package com.threetech.catpuzzle.domain.sudoku.model

/**
 * Per-tier stats. [plays] = puzzles started (also the seed index of the next puzzle), [wins] = puzzles solved.
 * [bestTimeMs] null until the first win.
 */
data class SudokuProgress(
    val tier: SudokuTier,
    val wins: Int = 0,
    val plays: Int = 0,
    val bestTimeMs: Long? = null,
    val bestScore: Int = 0,
)

/**
 * Picker row: stats + unlock state + whether a saved game waits in this tier.
 * [requirementWins] = wins so far in the tier's unlock tier (0 when the tier is always open);
 * the tier needs its unlockWins.
 */
data class SudokuTierStatus(
    val tier: SudokuTier,
    val progress: SudokuProgress,
    val unlocked: Boolean,
    val hasActiveGame: Boolean,
    val requirementWins: Int,
)
