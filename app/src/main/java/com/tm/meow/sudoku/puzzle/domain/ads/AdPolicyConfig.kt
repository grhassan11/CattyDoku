package com.tm.meow.sudoku.puzzle.domain.ads

/** Rewarded surfaces, each with its own remote switch (see [AdPolicyConfig.rewardedShowFor]). */
enum class RewardedPlacement {
    /** Levels booster "Need one?" dialog + empty-booster play badge (remote `levels_game_boosters_rewarded_ad_show`). */
    LEVELS_BOOSTERS,

    /** Levels lose-screen revive heart (remote `levels_game_revive_rewarded_ad_show`). */
    LEVELS_REVIVE,

    /** Sudoku out-of-hints dialog (remote `sudoku_game_boosters_rewarded_ad_show`). */
    SUDOKU_BOOSTERS,

    /** Sudoku game-over revive (remote `sudoku_game_revive_rewarded_ad_show`). */
    SUDOKU_REVIVE,
}

/**
 * Live source of the ad policy. Consumers call [current] ONCE per decision (atomic read) instead of capturing a config at
 * construction, so a Remote Config value activated mid-session (late fetch, B+C) applies to the next decision. A plain
 * [AdPolicyConfig] is itself a fixed source (defaults / tests).
 */
fun interface AdPolicySource {
    fun current(): AdPolicyConfig
}

/**
 * Ad policy constants in ONE place. Defaults ship in the app; Remote Config overrides by providing a different instance
 * in AdsModule. Nothing else reads raw numbers. There is NO master switch: every format has its own per-surface switch.
 */
data class AdPolicyConfig(
    /** No longer gates anything in game ads (rewarded, interstitials, natives); kept for RC compat. Was: Levels 1..this ad-free; NOT for the Levels win / lose-retry interstitials or the game native slots (see [AdFreePolicy]). Ads start at this + 1 (default: Lv 10). */
    val adFreeThroughLevel: Int = AdFreePolicy.AD_FREE_THROUGH_LEVEL,
    /**
     * Levels win interstitial frequency N (remote `levels_game_completed_interstitial_ad_frequency`, 0..50, 0 = Levels win interstitial OFF): applies ONLY
     * to levels ABOVE 10; an ad is considered when the JUST-COMPLETED level number is a multiple of N (N=1: 11, 12, 13...;
     * N=10: 20, 30, 40...). Levels 1..10 are governed by [levelsFirstTenInterstitialLevels] instead. The ad-free levels
     * ([adFreeThroughLevel]) do NOT apply to this interstitial.
     */
    val levelsInterstitialFrequency: Int = DEFAULT_LEVELS_INTERSTITIAL_FREQUENCY,
    /**
     * Sudoku win interstitial frequency N (remote `sudoku_game_completed_interstitial_ad_frequency`, 0..50, 0 = Sudoku win interstitial OFF), all tiers:
     * shown when the tier's lifetime win count is a multiple of N (from win 1; no ad-free gate).
     */
    val sudokuWinInterstitialFrequency: Int = DEFAULT_SUDOKU_INTERSTITIAL_FREQUENCY,
    /** Max wait for a still-loading preloaded ad at the boundary, then fail open (continue without ad). */
    val loadWaitTimeoutMs: Long = DEFAULT_LOAD_WAIT_TIMEOUT_MS,
    /** Levels Home bottom native ad (remote `level_home_native_ad_show`). */
    val levelsHomeNativeShow: Boolean = DEFAULT_SHOW,
    /** Sudoku entry bottom native ad (remote `sudoku_home_native_ad_show`). */
    val sudokuHomeNativeShow: Boolean = DEFAULT_SHOW,
    /** Levels game-screen compact native slot (remote `levels_game_native_ad_show`). */
    val levelsGameNativeShow: Boolean = DEFAULT_SHOW,
    /** Sudoku game-screen compact native slot (remote `sudoku_game_native_ad_show`). */
    val sudokuGameNativeShow: Boolean = DEFAULT_SHOW,
    /** Levels Level Completed (win) screen sticky bottom native (remote `levels_game_completed_native_ad_show`). */
    val levelsCompleteNativeShow: Boolean = DEFAULT_SHOW,
    /** Sudoku Puzzle solved (win) screen sticky bottom native (remote `sudoku_game_completed_native_ad_show`). */
    val sudokuCompleteNativeShow: Boolean = DEFAULT_SHOW,
    /** Interstitial on Levels win (derived: remote `levels_game_completed_interstitial_ad_frequency` > 0). */
    val levelsWinInterstitialShow: Boolean = DEFAULT_SHOW,
    /** Interstitial on Sudoku win (derived: remote `sudoku_game_completed_interstitial_ad_show` AND frequency > 0), ANDed with [SudokuAdRules]. */
    val sudokuWinInterstitialShow: Boolean = DEFAULT_SHOW,
    /** Interstitial on Levels lose-dialog Retry (remote `levels_game_restarted_interstitial_ad_show`). */
    val levelsRestartInterstitialShow: Boolean = DEFAULT_SHOW,
    /** Interstitial on Sudoku restart / game-over Retry (remote `sudoku_game_restarted_interstitial_ad_show`), ANDed with [SudokuAdRules]. */
    val sudokuRestartInterstitialShow: Boolean = DEFAULT_SHOW,
    /** Interstitial on Levels / Daily gameplay BACK (remote `levels_gameplay_back_button_native_ad_show`). */
    val levelsBackInterstitialShow: Boolean = DEFAULT_SHOW,
    /** Interstitial on Sudoku gameplay BACK (remote `sudoku_game_back_interstitial_ad_show`). */
    val sudokuBackInterstitialShow: Boolean = DEFAULT_SHOW,
    /** Levels booster rewarded (remote `levels_game_boosters_rewarded_ad_show`). */
    val levelsBoostersRewardedShow: Boolean = DEFAULT_SHOW,
    /** Sudoku hint rewarded (remote `sudoku_game_boosters_rewarded_ad_show`). */
    val sudokuBoostersRewardedShow: Boolean = DEFAULT_SHOW,
    /** Levels revive rewarded (remote `levels_game_revive_rewarded_ad_show`). */
    val levelsReviveRewardedShow: Boolean = DEFAULT_SHOW,
    /** Sudoku revive rewarded (remote `sudoku_game_revive_rewarded_ad_show`). */
    val sudokuReviveRewardedShow: Boolean = DEFAULT_SHOW,
    /**
     * Levels 1..10 only (remote `levels_game_first_10_levels_completed_interstitial_ad_frequency`, comma list): the win
     * interstitial shows after completing exactly these levels. Levels 1..10 ignore [levelsInterstitialFrequency].
     */
    val levelsFirstTenInterstitialLevels: Set<Int> = DEFAULT_LEVELS_FIRST_TEN_INTERSTITIAL_LEVELS,
) : AdPolicySource {
    override fun current(): AdPolicyConfig = this

    /** False = no interstitial surface is on: never preload or request one. */
    val anyInterstitialShow: Boolean
        get() = levelsWinInterstitialShow || sudokuWinInterstitialShow ||
            levelsRestartInterstitialShow || sudokuRestartInterstitialShow ||
            levelsBackInterstitialShow || sudokuBackInterstitialShow

    /** False = no rewarded surface is on: never preload one. */
    val anyRewardedShow: Boolean
        get() = levelsBoostersRewardedShow || sudokuBoostersRewardedShow ||
            levelsReviveRewardedShow || sudokuReviveRewardedShow

    fun rewardedShowFor(placement: RewardedPlacement): Boolean = when (placement) {
        RewardedPlacement.LEVELS_BOOSTERS -> levelsBoostersRewardedShow
        RewardedPlacement.LEVELS_REVIVE -> levelsReviveRewardedShow
        RewardedPlacement.SUDOKU_BOOSTERS -> sudokuBoostersRewardedShow
        RewardedPlacement.SUDOKU_REVIVE -> sudokuReviveRewardedShow
    }

    companion object {
        const val DEFAULT_LEVELS_INTERSTITIAL_FREQUENCY = 1
        const val DEFAULT_SUDOKU_INTERSTITIAL_FREQUENCY = 1
        const val DEFAULT_LOAD_WAIT_TIMEOUT_MS = 3_000L

        /** Last level governed by the first-10 list; levels above use the frequency N. */
        const val FIRST_TEN_LAST_LEVEL = 10
        val DEFAULT_LEVELS_FIRST_TEN_INTERSTITIAL_LEVELS: Set<Int> = setOf(2, 4, 6, 8, 10)

        /** Default of every per-surface show switch. */
        const val DEFAULT_SHOW = true
    }
}
