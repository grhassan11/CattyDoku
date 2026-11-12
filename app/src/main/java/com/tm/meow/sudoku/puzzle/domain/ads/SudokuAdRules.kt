package com.tm.meow.sudoku.puzzle.domain.ads

import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier

/**
 * Pure Sudoku interstitial rules (product spec). Consent / Remove Ads / kill switch are enforced afterwards by
 * [InterstitialPolicy]; ad readiness / load failure by the AdsRepository (fail open).
 *
 * Win, Easy: from its [EASY_FIRST_AD_WIN]th lifetime win on, every win after.
 * Win, other tiers: tier win N (incl. the one just recorded) must be > adFreeThroughLevel and N % frequency == 0.
 * Restart: every tier but Easy shows. Easy never.
 */
object SudokuAdRules {
    const val EASY_FIRST_AD_WIN = 5

    /**
     * [tierWinsIncludingThis] = lifetime wins in [tier] AFTER the win being continued from was recorded (null = unknown,
     * never shows). [frequency] coerced to at least 1; [adFreeThroughLevel] = shared remote ad-free threshold.
     */
    fun showOnWin(
        tier: SudokuTier,
        tierWinsIncludingThis: Int?,
        frequency: Int,
        adFreeThroughLevel: Int,
    ): Boolean {
        val n = tierWinsIncludingThis ?: return false
        if (tier == SudokuTier.EASY) return n >= EASY_FIRST_AD_WIN
        return n > adFreeThroughLevel && n % frequency.coerceAtLeast(1) == 0
    }

    fun showOnRestart(tier: SudokuTier): Boolean = tier != SudokuTier.EASY
}
