package com.tm.meow.sudoku.puzzle.domain.ads

import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier

/**
 * Pure Sudoku interstitial rules (product spec). Consent / Remove Ads / kill switch are enforced afterwards by
 * [InterstitialPolicy]; ad readiness / load failure by the AdsRepository (fail open).
 *
 * Win, every tier: tier win N (incl. the one just recorded) with N % frequency == 0, from win 1. No ad-free gate.
 * Restart: every tier but Easy shows. Easy never.
 */
object SudokuAdRules {
    /**
     * [tierWinsIncludingThis] = lifetime wins in [tier] AFTER the win being continued from was recorded (null = unknown,
     * never shows). [frequency] coerced to at least 1.
     */
    fun showOnWin(
        tier: SudokuTier,
        tierWinsIncludingThis: Int?,
        frequency: Int,
    ): Boolean {
        val n = tierWinsIncludingThis ?: return false
        return n % frequency.coerceAtLeast(1) == 0
    }

    fun showOnRestart(tier: SudokuTier): Boolean = tier != SudokuTier.EASY
}
