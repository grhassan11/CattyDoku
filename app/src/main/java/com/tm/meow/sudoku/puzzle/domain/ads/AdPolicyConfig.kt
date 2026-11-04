package com.tm.meow.sudoku.puzzle.domain.ads

/**
 * Interstitial policy constants in ONE place. Defaults ship in the app; phase 10 (Firebase Remote Config)
 * overrides by providing a different instance in AdsModule. Nothing else reads raw numbers.
 */
data class AdPolicyConfig(
    /** Master switch (remote kill-switch seam). */
    val enabled: Boolean = DEFAULT_ENABLED,
    /** Levels 1..this are ad-free for EVERY format (see [AdFreePolicy]). Ads start at this + 1 (default: Lv 10). */
    val adFreeThroughLevel: Int = AdFreePolicy.AD_FREE_THROUGH_LEVEL,
    /** Show on every Nth win since the last ad (default: every 2nd win). */
    val everyNthWin: Int = DEFAULT_EVERY_NTH_WIN,
    /** No ad until the process has been alive this long (first minutes of a session). */
    val minSessionAgeMs: Long = DEFAULT_MIN_SESSION_AGE_MS,
    /** Minimum gap between two interstitials (monotonic clock). */
    val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    /** Hard cap per process. */
    val maxPerSession: Int = DEFAULT_MAX_PER_SESSION,
    /** Lose-screen Retry boundary. OFF by default; when on, only min interval / session age / cap apply. */
    val showOnLoseRetry: Boolean = DEFAULT_SHOW_ON_LOSE_RETRY,
    /** Max wait for a still-loading preloaded ad at the boundary, then fail open (continue without ad). */
    val loadWaitTimeoutMs: Long = DEFAULT_LOAD_WAIT_TIMEOUT_MS,
    /** Levels Home bottom native ad switch (remote `levels_home_native_ad_show`). Also gated by [enabled]. */
    val levelsHomeNativeShow: Boolean = DEFAULT_SHOW,
    /** Sudoku entry bottom native ad switch (remote `sudoku_home_native_ad_show`). Also gated by [enabled]. */
    val sudokuHomeNativeShow: Boolean = DEFAULT_SHOW,
    /** Levels game-screen sticky banner switch (remote `levels_game_banner_ad_show`). Also gated by [enabled]. */
    val levelsGameBannerShow: Boolean = DEFAULT_SHOW,
    /** Sudoku game-screen sticky banner switch (remote `sudoku_game_banner_ad_show`). Also gated by [enabled]. */
    val sudokuGameBannerShow: Boolean = DEFAULT_SHOW,
    /** Rewarded surfaces (booster play badge + dialog, lose-screen revive). Also gated by [enabled]. */
    val rewardedShow: Boolean = DEFAULT_SHOW,
    /** Interstitial switch (remote `interstitial_show`): false = never preload, request or show. Also gated by [enabled]. */
    val interstitialShow: Boolean = DEFAULT_SHOW,
) {
    companion object {
        const val DEFAULT_ENABLED = true
        const val DEFAULT_EVERY_NTH_WIN = 2
        const val DEFAULT_MIN_SESSION_AGE_MS = 120_000L
        const val DEFAULT_MIN_INTERVAL_MS = 90_000L
        const val DEFAULT_MAX_PER_SESSION = 6
        const val DEFAULT_SHOW_ON_LOSE_RETRY = false
        const val DEFAULT_LOAD_WAIT_TIMEOUT_MS = 3_000L
        /** Default of every per-unit show switch. */
        const val DEFAULT_SHOW = true
    }
}
