package com.tm.meow.sudoku.puzzle.domain.ads

import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier

/**
 * Pure Sudoku interstitial rules (product spec). Consent / Remove Ads / kill switch are enforced afterwards by
 * [InterstitialPolicy]; ad readiness / load failure by the AdsRepository (fail open).
 *
 * Win: every tier but Easy shows on every win. Easy shows from its [EASY_FIRST_AD_WIN]th lifetime win on, every win after.
 * Restart: every tier but Easy shows. Easy never.
 */
object SudokuAdRules {
    const val EASY_FIRST_AD_WIN = 5

    /** [tierWinsIncludingThis] = lifetime wins in [tier] AFTER the win being continued from was recorded (null = unknown). */
    fun showOnWin(tier: SudokuTier, tierWinsIncludingThis: Int?): Boolean =
        if (tier == SudokuTier.EASY) (tierWinsIncludingThis ?: 0) >= EASY_FIRST_AD_WIN else true

    fun showOnRestart(tier: SudokuTier): Boolean = tier != SudokuTier.EASY
}
