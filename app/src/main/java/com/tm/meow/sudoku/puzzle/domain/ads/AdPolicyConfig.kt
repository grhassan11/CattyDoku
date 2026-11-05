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
 * Ad policy constants in ONE place. Defaults ship in the app; Remote Config overrides by providing a different instance
 * in AdsModule. Nothing else reads raw numbers. There is NO master switch: every format has its own per-surface switch.
 */
data class AdPolicyConfig(
    /** Levels 1..this are ad-free for EVERY format (see [AdFreePolicy]). Ads start at this + 1 (default: Lv 10). */
    val adFreeThroughLevel: Int = AdFreePolicy.AD_FREE_THROUGH_LEVEL,
    /**
     * Levels win interstitial frequency N (remote `levels_game_completed_interstitial_ad_frequency`, 1..50): an ad is
     * considered only when the JUST-COMPLETED level number is a multiple of N (and past the ad-free levels).
     */
    val levelsInterstitialFrequency: Int = DEFAULT_LEVELS_INTERSTITIAL_FREQUENCY,
    /** No ad until the process has been alive this long (first minutes of a session). */
    val minSessionAgeMs: Long = DEFAULT_MIN_SESSION_AGE_MS,
    /** Minimum gap between two interstitials (monotonic clock). Hardcoded, not remote. */
    val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    /** Hard cap per process. */
    val maxPerSession: Int = DEFAULT_MAX_PER_SESSION,
    /** Max wait for a still-loading preloaded ad at the boundary, then fail open (continue without ad). */
    val loadWaitTimeoutMs: Long = DEFAULT_LOAD_WAIT_TIMEOUT_MS,
    /** Levels Home bottom native ad (remote `level_home_native_ad_show`). */
    val levelsHomeNativeShow: Boolean = DEFAULT_SHOW,
    /** Sudoku entry bottom native ad (remote `sudoku_home_native_ad_show`). */
    val sudokuHomeNativeShow: Boolean = DEFAULT_SHOW,
    /** Levels game-screen sticky banner (remote `levels_game_banner_ad_show`). */
    val levelsGameBannerShow: Boolean = DEFAULT_SHOW,
    /** Sudoku game-screen sticky banner (remote `sudoku_game_banner_ad_show`). */
    val sudokuGameBannerShow: Boolean = DEFAULT_SHOW,
    /** Interstitial on Levels win (remote `levels_game_completed_interstitial_ad_show`). */
    val levelsWinInterstitialShow: Boolean = DEFAULT_SHOW,
    /** Interstitial on Sudoku win (remote `sudoku_game_completed_interstitial_ad_show`), ANDed with [SudokuAdRules]. */
    val sudokuWinInterstitialShow: Boolean = DEFAULT_SHOW,
    /** Interstitial on Levels lose-dialog Retry (remote `levels_game_restarted_interstitial_ad_show`). */
    val levelsRestartInterstitialShow: Boolean = DEFAULT_SHOW,
    /** Interstitial on Sudoku restart / game-over Retry (remote `sudoku_game_restarted_interstitial_ad_show`), ANDed with [SudokuAdRules]. */
    val sudokuRestartInterstitialShow: Boolean = DEFAULT_SHOW,
    /** Levels booster rewarded (remote `levels_game_boosters_rewarded_ad_show`). */
    val levelsBoostersRewardedShow: Boolean = DEFAULT_SHOW,
    /** Sudoku hint rewarded (remote `sudoku_game_boosters_rewarded_ad_show`). */
    val sudokuBoostersRewardedShow: Boolean = DEFAULT_SHOW,
    /** Levels revive rewarded (remote `levels_game_revive_rewarded_ad_show`). */
    val levelsReviveRewardedShow: Boolean = DEFAULT_SHOW,
    /** Sudoku revive rewarded (remote `sudoku_game_revive_rewarded_ad_show`). */
    val sudokuReviveRewardedShow: Boolean = DEFAULT_SHOW,
) {
    /** False = no interstitial surface is on: never preload or request one. */
    val anyInterstitialShow: Boolean
        get() = levelsWinInterstitialShow || sudokuWinInterstitialShow ||
            levelsRestartInterstitialShow || sudokuRestartInterstitialShow

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
        const val DEFAULT_LEVELS_INTERSTITIAL_FREQUENCY = 5
        const val DEFAULT_MIN_SESSION_AGE_MS = 120_000L
        const val DEFAULT_MIN_INTERVAL_MS = 90_000L
        const val DEFAULT_MAX_PER_SESSION = 6
        const val DEFAULT_LOAD_WAIT_TIMEOUT_MS = 3_000L

        /** Default of every per-surface show switch. */
        const val DEFAULT_SHOW = true
    }
}
