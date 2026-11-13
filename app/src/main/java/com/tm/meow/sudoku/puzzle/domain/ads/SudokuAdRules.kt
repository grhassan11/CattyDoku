package com.tm.meow.sudoku.puzzle.domain.ads

import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier

/**
 * Pure Sudoku interstitial rules (product spec). Consent / Remove Ads / kill switch are enforced afterwards by
 * [InterstitialPolicy]; ad readiness / load failure by the AdsRepository (fail open).
 *
 * Win, every tier: tier win N (incl. the one just recorded) with N % frequency == 0, from win 1. No ad-free gate.
 * Restart: every tier shows.
 */
object SudokuAdRules {
    /**
     * [tierWinsIncludingThis] = lifetime wins in [tier] AFTER the win being continued from was recorded (null = unknown,
     * shows: fail toward showing). [frequency] coerced to at least 1.
     */
    fun showOnWin(
        tier: SudokuTier,
        tierWinsIncludingThis: Int?,
        frequency: Int,
    ): Boolean {
        val n = tierWinsIncludingThis ?: return true
        return n % frequency.coerceAtLeast(1) == 0
    }

    fun showOnRestart(@Suppress("UNUSED_PARAMETER") tier: SudokuTier): Boolean = true
}
