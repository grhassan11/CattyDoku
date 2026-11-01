package com.threetech.catpuzzle.ui.screens.sudoku.entry

import androidx.compose.runtime.Immutable
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTier

/** One difficulty row. [winsHave]/[winsNeeded] feed the locked pill (wins in [requiredTier] so far / needed). */
@Immutable
data class SudokuTierRowUi(
    val tier: SudokuTier,
    val gridSize: Int,
    val locked: Boolean,
    val winsHave: Int,
    val winsNeeded: Int,
    val requiredTier: SudokuTier?,
    val hasActiveGame: Boolean,
)

/** Stats of the selected tier; null on [SudokuEntryUiState.stats] = no win yet. */
@Immutable
data class SudokuStatsUi(val wins: Int, val bestTime: String, val bestScore: Int)

@Immutable
data class SudokuEntryUiState(
    val rows: List<SudokuTierRowUi>,
    val selected: SudokuTier,
    val stats: SudokuStatsUi?,
    /** True when the selected tier is open and has a saved game: shows CONTINUE. */
    val canContinue: Boolean,
    /** "mm:ss" saved timer for the CONTINUE pill (null when canContinue is false). */
    val continueTimer: String?,
    /** NEW GAME hit a tier with a saved game: ask before discarding it. */
    val confirmNewGame: Boolean = false,
)

sealed interface SudokuEntryEvent {
    /** Locked tier tapped: show the requirement. */
    data class Locked(val tier: SudokuTier, val requiredTier: SudokuTier, val winsNeeded: Int, val winsHave: Int) : SudokuEntryEvent

    /** Navigate to the game. [resume] false = a fresh puzzle (save was just cleared). */
    data class OpenGame(val tier: SudokuTier, val resume: Boolean) : SudokuEntryEvent
}
