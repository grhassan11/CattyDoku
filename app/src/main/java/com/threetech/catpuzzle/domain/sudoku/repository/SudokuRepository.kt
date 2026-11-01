package com.threetech.catpuzzle.domain.sudoku.repository

import com.threetech.catpuzzle.domain.sudoku.model.SudokuGameState
import com.threetech.catpuzzle.domain.sudoku.model.SudokuProgress
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTier
import kotlinx.coroutines.flow.Flow

/**
 * Sudoku persistence. Separate from the Queens repositories.
 *
 * Active game: ONE save PER TIER (a player can keep an Easy game open while a Hard one is in progress). The save
 * holds the puzzle itself (givens + solution), so a saved game never regenerates, whatever the generator version.
 */
interface SudokuRepository {
    /** One entry per tier (zeroed when never played). */
    fun observeProgress(): Flow<Map<SudokuTier, SudokuProgress>>

    /** Tiers that currently have a saved game. */
    fun observeActiveTiers(): Flow<Set<SudokuTier>>

    suspend fun getProgress(tier: SudokuTier): SudokuProgress

    suspend fun winsByTier(): Map<SudokuTier, Int>

    /** Atomically count a started puzzle and return the new plays total (1-based). Use it as the seed index. */
    suspend fun nextPlayIndex(tier: SudokuTier): Int

    /** Count a win (wins + 1, best time = min, best score = max) and return the stored progress. */
    suspend fun recordWin(tier: SudokuTier, timeMs: Long, score: Int): SudokuProgress

    /** Null when no save, or the stored row was corrupt (dropped). Restored state is unpaused and unselected. */
    suspend fun getActiveGame(tier: SudokuTier): SudokuGameState?

    suspend fun saveActiveGame(state: SudokuGameState)

    suspend fun clearActiveGame(tier: SudokuTier)
}
