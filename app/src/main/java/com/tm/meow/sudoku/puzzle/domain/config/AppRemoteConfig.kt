package com.tm.meow.sudoku.puzzle.domain.config

import com.tm.meow.sudoku.puzzle.domain.ads.AdFreePolicy
import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicyConfig
import com.tm.meow.sudoku.puzzle.domain.engine.DifficultyCurve

/** Remote Config key names. STABLE: they are the contract with the Firebase console. */
object RemoteConfigKeys {
    const val AD_FREE_THROUGH_LEVEL = "ad_free_through_level"
    const val LEVEL_HOME_NATIVE_AD_SHOW = "level_home_native_ad_show"
    const val SUDOKU_HOME_NATIVE_AD_SHOW = "sudoku_home_native_ad_show"
    const val LEVELS_GAME_NATIVE_AD_SHOW = "levels_game_native_ad_show"
    const val SUDOKU_GAME_NATIVE_AD_SHOW = "sudoku_game_native_ad_show"
    const val SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_SHOW = "sudoku_game_completed_interstitial_ad_show"
    const val LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_SHOW = "levels_game_completed_interstitial_ad_show"
    const val LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY = "levels_game_completed_interstitial_ad_frequency"
    const val LEVELS_GAME_FIRST_10_LEVELS_COMPLETED_INTERSTITIAL_AD_FREQUENCY = "levels_game_first_10_levels_completed_interstitial_ad_frequency"
    const val SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY = "sudoku_game_completed_interstitial_ad_frequency"
    const val SUDOKU_GAME_RESTARTED_INTERSTITIAL_AD_SHOW ="sudoku_game_restarted_interstitial_ad_show"
    const val LEVELS_GAME_RESTARTED_INTERSTITIAL_AD_SHOW = "levels_game_restarted_interstitial_ad_show"
    const val SUDOKU_GAME_BOOSTERS_REWARDED_AD_SHOW = "sudoku_game_boosters_rewarded_ad_show"
    const val LEVELS_GAME_BOOSTERS_REWARDED_AD_SHOW = "levels_game_boosters_rewarded_ad_show"
    const val LEVELS_GAME_REVIVE_REWARDED_AD_SHOW = "levels_game_revive_rewarded_ad_show"
    const val SUDOKU_GAME_REVIVE_REWARDED_AD_SHOW = "sudoku_game_revive_rewarded_ad_show"

    /** Kill switch for streak milestone rewards (amounts are hardcoded, never remote). */
    const val STREAK_REWARDS_ENABLED = "streak_rewards_enabled"

    /** Kill switch for the evening streak-at-risk notification. */
    const val STREAK_REMINDER_ENABLED = "streak_reminder_enabled"

    /** Kill switch for the daily 20:00 notification. */
    const val DAILY_REMINDER_ENABLED = "daily_reminder_enabled"

    /** Kill switch for the splash app-open ad (preload + show). Lifecycle app-open is unaffected. */
    const val SPLASH_APP_OPEN_AD_SHOW = "splash_app_open_ad_show"

    /** Levels 1..N get one forced 1-cell region (single colored block). 0 = off. Applies to levels not yet generated. */
    const val LEVELS_SINGLE_COLORED_BLOCK = "levels_single_colored_block"
}

/**
 * Typed Remote Config snapshot. Defaults here ARE the in-app defaults (they equal the shipped ad policy), so a missing
 * or failing config changes nothing. Difficulty curve is deliberately NOT remote (puzzle determinism, generatorVersion);
 * the one exception is [levelsSingleColoredBlock] (only affects levels not yet in the puzzle cache).
 * No master ads switch: each surface has its own key. Interstitial min interval is a hardcoded constant.
 */
data class AppRemoteConfig(
    val adFreeThroughLevel: Int = AdFreePolicy.AD_FREE_THROUGH_LEVEL,
    val levelHomeNativeAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val sudokuHomeNativeAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val levelsGameNativeAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val sudokuGameNativeAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val sudokuGameCompletedInterstitialAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val levelsGameCompletedInterstitialAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    /** Levels win ad on every Nth completed level ABOVE 10 (level % N == 0), 1..50. Levels 1..10 use the list below. */
    val levelsGameCompletedInterstitialAdFrequency: Int = AdPolicyConfig.DEFAULT_LEVELS_INTERSTITIAL_FREQUENCY,
    /** Levels 1..10 win ad after exactly these levels (remote comma list, default "2,4,6,8,10"). */
    val levelsGameFirst10LevelsCompletedInterstitialAdLevels: Set<Int> = AdPolicyConfig.DEFAULT_LEVELS_FIRST_TEN_INTERSTITIAL_LEVELS,
    /** Sudoku win ad on every Nth tier win (N % freq == 0), 1..50, all tiers from win 1 (no ad-free gate). */
    val sudokuGameCompletedInterstitialAdFrequency: Int = AdPolicyConfig.DEFAULT_SUDOKU_INTERSTITIAL_FREQUENCY,
    val sudokuGameRestartedInterstitialAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val levelsGameRestartedInterstitialAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val sudokuGameBoostersRewardedAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val levelsGameBoostersRewardedAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val levelsGameReviveRewardedAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val sudokuGameReviveRewardedAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    /** Streak milestone rewards on/off. false hides the reward UI and refuses claims. */
    val streakRewardsEnabled: Boolean = true,
    /** Streak-at-risk evening notification on/off. false = nothing scheduled, nothing shown. */
    val streakReminderEnabled: Boolean = true,
    /** Daily 20:00 notification on/off. false = nothing scheduled, nothing shown. */
    val dailyReminderEnabled: Boolean = true,
    /** Splash app-open ad on/off. false = no splash preload, no splash show (lifecycle app-open unchanged). */
    val splashAppOpenAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    /** Levels 1..N get the single colored block (forced 1-cell region), 0..500, 0 = off. Cached levels never change. */
    val levelsSingleColoredBlock: Int = DifficultyCurve.SINGLE_BLOCK_THROUGH_LEVEL,
) {
    fun toAdPolicyConfig(): AdPolicyConfig = AdPolicyConfig(
        adFreeThroughLevel = adFreeThroughLevel,
        levelsInterstitialFrequency = levelsGameCompletedInterstitialAdFrequency,
        levelsFirstTenInterstitialLevels = levelsGameFirst10LevelsCompletedInterstitialAdLevels,
        sudokuWinInterstitialFrequency = sudokuGameCompletedInterstitialAdFrequency,
        levelsHomeNativeShow = levelHomeNativeAdShow,
        sudokuHomeNativeShow = sudokuHomeNativeAdShow,
        levelsGameNativeShow = levelsGameNativeAdShow,
        sudokuGameNativeShow = sudokuGameNativeAdShow,
        levelsWinInterstitialShow = levelsGameCompletedInterstitialAdShow,
        sudokuWinInterstitialShow = sudokuGameCompletedInterstitialAdShow,
        levelsRestartInterstitialShow = levelsGameRestartedInterstitialAdShow,
        sudokuRestartInterstitialShow = sudokuGameRestartedInterstitialAdShow,
        levelsBoostersRewardedShow = levelsGameBoostersRewardedAdShow,
        sudokuBoostersRewardedShow = sudokuGameBoostersRewardedAdShow,
        levelsReviveRewardedShow = levelsGameReviveRewardedAdShow,
        sudokuReviveRewardedShow = sudokuGameReviveRewardedAdShow,
    )

    companion object {
        val AD_FREE_LEVEL_RANGE = 0..50
        val INTERSTITIAL_FREQUENCY_RANGE = 1..50
        val SINGLE_COLORED_BLOCK_RANGE = 0..500
    }
}

/** Pure parsing of raw string values (blank / missing / malformed / out of range = in-app default). */
object RemoteConfigParser {

    fun parse(read: (String) -> String?): AppRemoteConfig {
        val d = AppRemoteConfig()
        return AppRemoteConfig(
            adFreeThroughLevel = int(
                read(RemoteConfigKeys.AD_FREE_THROUGH_LEVEL), d.adFreeThroughLevel, AppRemoteConfig.AD_FREE_LEVEL_RANGE,
            ),
            levelHomeNativeAdShow = bool(read(RemoteConfigKeys.LEVEL_HOME_NATIVE_AD_SHOW), d.levelHomeNativeAdShow),
            sudokuHomeNativeAdShow = bool(read(RemoteConfigKeys.SUDOKU_HOME_NATIVE_AD_SHOW), d.sudokuHomeNativeAdShow),
            levelsGameNativeAdShow = bool(read(RemoteConfigKeys.LEVELS_GAME_NATIVE_AD_SHOW), d.levelsGameNativeAdShow),
            sudokuGameNativeAdShow = bool(read(RemoteConfigKeys.SUDOKU_GAME_NATIVE_AD_SHOW), d.sudokuGameNativeAdShow),
            sudokuGameCompletedInterstitialAdShow = bool(
                read(RemoteConfigKeys.SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_SHOW), d.sudokuGameCompletedInterstitialAdShow,
            ),
            levelsGameCompletedInterstitialAdShow = bool(
                read(RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_SHOW), d.levelsGameCompletedInterstitialAdShow,
            ),
            levelsGameCompletedInterstitialAdFrequency = int(
                read(RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY),
                d.levelsGameCompletedInterstitialAdFrequency,
                AppRemoteConfig.INTERSTITIAL_FREQUENCY_RANGE,
            ),
            levelsGameFirst10LevelsCompletedInterstitialAdLevels = levelList(
                read(RemoteConfigKeys.LEVELS_GAME_FIRST_10_LEVELS_COMPLETED_INTERSTITIAL_AD_FREQUENCY),
                d.levelsGameFirst10LevelsCompletedInterstitialAdLevels,
            ),
            sudokuGameCompletedInterstitialAdFrequency = int(
                read(RemoteConfigKeys.SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY),
                d.sudokuGameCompletedInterstitialAdFrequency,
                AppRemoteConfig.INTERSTITIAL_FREQUENCY_RANGE,
            ),
            sudokuGameRestartedInterstitialAdShow = bool(
                read(RemoteConfigKeys.SUDOKU_GAME_RESTARTED_INTERSTITIAL_AD_SHOW), d.sudokuGameRestartedInterstitialAdShow,
            ),
            levelsGameRestartedInterstitialAdShow = bool(
                read(RemoteConfigKeys.LEVELS_GAME_RESTARTED_INTERSTITIAL_AD_SHOW), d.levelsGameRestartedInterstitialAdShow,
            ),
            sudokuGameBoostersRewardedAdShow = bool(
                read(RemoteConfigKeys.SUDOKU_GAME_BOOSTERS_REWARDED_AD_SHOW), d.sudokuGameBoostersRewardedAdShow,
            ),
            levelsGameBoostersRewardedAdShow = bool(
                read(RemoteConfigKeys.LEVELS_GAME_BOOSTERS_REWARDED_AD_SHOW), d.levelsGameBoostersRewardedAdShow,
            ),
            levelsGameReviveRewardedAdShow = bool(
                read(RemoteConfigKeys.LEVELS_GAME_REVIVE_REWARDED_AD_SHOW), d.levelsGameReviveRewardedAdShow,
            ),
            sudokuGameReviveRewardedAdShow = bool(
                read(RemoteConfigKeys.SUDOKU_GAME_REVIVE_REWARDED_AD_SHOW), d.sudokuGameReviveRewardedAdShow,
            ),
            streakRewardsEnabled = bool(read(RemoteConfigKeys.STREAK_REWARDS_ENABLED), d.streakRewardsEnabled),
            streakReminderEnabled = bool(read(RemoteConfigKeys.STREAK_REMINDER_ENABLED), d.streakReminderEnabled),
            dailyReminderEnabled = bool(read(RemoteConfigKeys.DAILY_REMINDER_ENABLED), d.dailyReminderEnabled),
            splashAppOpenAdShow = bool(read(RemoteConfigKeys.SPLASH_APP_OPEN_AD_SHOW), d.splashAppOpenAdShow),
            levelsSingleColoredBlock = int(
                read(RemoteConfigKeys.LEVELS_SINGLE_COLORED_BLOCK), d.levelsSingleColoredBlock,
                AppRemoteConfig.SINGLE_COLORED_BLOCK_RANGE,
            ),
        )
    }

    /** In-app defaults as raw strings, for the Remote Config SDK's setDefaults. */
    fun defaults(): Map<String, String> {
        val d = AppRemoteConfig()
        return mapOf(
            RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to d.adFreeThroughLevel.toString(),
            RemoteConfigKeys.LEVEL_HOME_NATIVE_AD_SHOW to d.levelHomeNativeAdShow.toString(),
            RemoteConfigKeys.SUDOKU_HOME_NATIVE_AD_SHOW to d.sudokuHomeNativeAdShow.toString(),
            RemoteConfigKeys.LEVELS_GAME_NATIVE_AD_SHOW to d.levelsGameNativeAdShow.toString(),
            RemoteConfigKeys.SUDOKU_GAME_NATIVE_AD_SHOW to d.sudokuGameNativeAdShow.toString(),
            RemoteConfigKeys.SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_SHOW to d.sudokuGameCompletedInterstitialAdShow.toString(),
            RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_SHOW to d.levelsGameCompletedInterstitialAdShow.toString(),
            RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY to d.levelsGameCompletedInterstitialAdFrequency.toString(),
            RemoteConfigKeys.LEVELS_GAME_FIRST_10_LEVELS_COMPLETED_INTERSTITIAL_AD_FREQUENCY to
                d.levelsGameFirst10LevelsCompletedInterstitialAdLevels.sorted().joinToString(","),
            RemoteConfigKeys.SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY to d.sudokuGameCompletedInterstitialAdFrequency.toString(),
            RemoteConfigKeys.SUDOKU_GAME_RESTARTED_INTERSTITIAL_AD_SHOW to d.sudokuGameRestartedInterstitialAdShow.toString(),
            RemoteConfigKeys.LEVELS_GAME_RESTARTED_INTERSTITIAL_AD_SHOW to d.levelsGameRestartedInterstitialAdShow.toString(),
            RemoteConfigKeys.SUDOKU_GAME_BOOSTERS_REWARDED_AD_SHOW to d.sudokuGameBoostersRewardedAdShow.toString(),
            RemoteConfigKeys.LEVELS_GAME_BOOSTERS_REWARDED_AD_SHOW to d.levelsGameBoostersRewardedAdShow.toString(),
            RemoteConfigKeys.LEVELS_GAME_REVIVE_REWARDED_AD_SHOW to d.levelsGameReviveRewardedAdShow.toString(),
            RemoteConfigKeys.SUDOKU_GAME_REVIVE_REWARDED_AD_SHOW to d.sudokuGameReviveRewardedAdShow.toString(),
            RemoteConfigKeys.STREAK_REWARDS_ENABLED to d.streakRewardsEnabled.toString(),
            RemoteConfigKeys.STREAK_REMINDER_ENABLED to d.streakReminderEnabled.toString(),
            RemoteConfigKeys.DAILY_REMINDER_ENABLED to d.dailyReminderEnabled.toString(),
            RemoteConfigKeys.SPLASH_APP_OPEN_AD_SHOW to d.splashAppOpenAdShow.toString(),
            RemoteConfigKeys.LEVELS_SINGLE_COLORED_BLOCK to d.levelsSingleColoredBlock.toString(),
        )
    }

    private fun bool(raw: String?, default: Boolean): Boolean = when (raw?.trim()?.lowercase()) {
        "true", "1", "t", "yes", "y", "on" -> true
        "false", "0", "f", "no", "n", "off" -> false
        else -> default
    }

    /**
     * Comma list of level numbers: split, trim, toIntOrNull, keep 1..10, dedupe. Bad tokens ignored; missing / blank /
     * no valid token = [default].
     */
    private fun levelList(raw: String?, default: Set<Int>): Set<Int> {
        val parsed = raw?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in 1..AdPolicyConfig.FIRST_TEN_LAST_LEVEL }?.toSet()
        return if (parsed.isNullOrEmpty()) default else parsed
    }

    /** Out of range = default (not clamped): a typo like 9999 must not silently become a policy value. */
    private fun int(raw: String?, default: Int, range: IntRange): Int {
        val v = raw?.trim()?.toIntOrNull() ?: return default
        return if (v in range) v else default
    }
}
