package com.tm.meow.sudoku.puzzle.domain.config

import com.tm.meow.sudoku.puzzle.domain.ads.AdFreePolicy
import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicyConfig
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenConfig

/** Remote Config key names. STABLE: they are the contract with the Firebase console. */
object RemoteConfigKeys {
    const val AD_FREE_THROUGH_LEVEL = "ad_free_through_level"
    const val APP_OPEN_AD_SHOW = "app_open_ad_show"
    const val LEVEL_HOME_NATIVE_AD_SHOW = "level_home_native_ad_show"
    const val SUDOKU_HOME_NATIVE_AD_SHOW = "sudoku_home_native_ad_show"
    const val LEVELS_GAME_BANNER_AD_SHOW = "levels_game_banner_ad_show"
    const val SUDOKU_GAME_BANNER_AD_SHOW = "sudoku_game_banner_ad_show"
    const val SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_SHOW = "sudoku_game_completed_interstitial_ad_show"
    const val LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_SHOW = "levels_game_completed_interstitial_ad_show"
    const val LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY = "levels_game_completed_interstitial_ad_frequency"
    const val SUDOKU_GAME_RESTARTED_INTERSTITIAL_AD_SHOW = "sudoku_game_restarted_interstitial_ad_show"
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
}

/**
 * Typed Remote Config snapshot. Defaults here ARE the in-app defaults (they equal the shipped ad policy), so a missing
 * or failing config changes nothing. Difficulty curve is deliberately NOT remote (puzzle determinism, generatorVersion).
 * No master ads switch: each surface has its own key. Interstitial min interval is a hardcoded constant.
 */
data class AppRemoteConfig(
    val adFreeThroughLevel: Int = AdFreePolicy.AD_FREE_THROUGH_LEVEL,
    /** App-wide app-open kill switch. */
    val appOpenAdShow: Boolean = AppOpenConfig.DEFAULT_ENABLED,
    val levelHomeNativeAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val sudokuHomeNativeAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val levelsGameBannerAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val sudokuGameBannerAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val sudokuGameCompletedInterstitialAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val levelsGameCompletedInterstitialAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    /** Levels win ad on every Nth completed level (level % N == 0), 1..50. */
    val levelsGameCompletedInterstitialAdFrequency: Int = AdPolicyConfig.DEFAULT_LEVELS_INTERSTITIAL_FREQUENCY,
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
) {
    fun toAdPolicyConfig(): AdPolicyConfig = AdPolicyConfig(
        adFreeThroughLevel = adFreeThroughLevel,
        levelsInterstitialFrequency = levelsGameCompletedInterstitialAdFrequency,
        levelsHomeNativeShow = levelHomeNativeAdShow,
        sudokuHomeNativeShow = sudokuHomeNativeAdShow,
        levelsGameBannerShow = levelsGameBannerAdShow,
        sudokuGameBannerShow = sudokuGameBannerAdShow,
        levelsWinInterstitialShow = levelsGameCompletedInterstitialAdShow,
        sudokuWinInterstitialShow = sudokuGameCompletedInterstitialAdShow,
        levelsRestartInterstitialShow = levelsGameRestartedInterstitialAdShow,
        sudokuRestartInterstitialShow = sudokuGameRestartedInterstitialAdShow,
        levelsBoostersRewardedShow = levelsGameBoostersRewardedAdShow,
        sudokuBoostersRewardedShow = sudokuGameBoostersRewardedAdShow,
        levelsReviveRewardedShow = levelsGameReviveRewardedAdShow,
        sudokuReviveRewardedShow = sudokuGameReviveRewardedAdShow,
    )

    fun toAppOpenConfig(): AppOpenConfig = AppOpenConfig(
        enabled = appOpenAdShow,
        adFreeThroughLevel = adFreeThroughLevel,
    )

    companion object {
        val AD_FREE_LEVEL_RANGE = 0..50
        val INTERSTITIAL_FREQUENCY_RANGE = 1..50
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
            appOpenAdShow = bool(read(RemoteConfigKeys.APP_OPEN_AD_SHOW), d.appOpenAdShow),
            levelHomeNativeAdShow = bool(read(RemoteConfigKeys.LEVEL_HOME_NATIVE_AD_SHOW), d.levelHomeNativeAdShow),
            sudokuHomeNativeAdShow = bool(read(RemoteConfigKeys.SUDOKU_HOME_NATIVE_AD_SHOW), d.sudokuHomeNativeAdShow),
            levelsGameBannerAdShow = bool(read(RemoteConfigKeys.LEVELS_GAME_BANNER_AD_SHOW), d.levelsGameBannerAdShow),
            sudokuGameBannerAdShow = bool(read(RemoteConfigKeys.SUDOKU_GAME_BANNER_AD_SHOW), d.sudokuGameBannerAdShow),
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
        )
    }

    /** In-app defaults as raw strings, for the Remote Config SDK's setDefaults. */
    fun defaults(): Map<String, String> {
        val d = AppRemoteConfig()
        return mapOf(
            RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to d.adFreeThroughLevel.toString(),
            RemoteConfigKeys.APP_OPEN_AD_SHOW to d.appOpenAdShow.toString(),
            RemoteConfigKeys.LEVEL_HOME_NATIVE_AD_SHOW to d.levelHomeNativeAdShow.toString(),
            RemoteConfigKeys.SUDOKU_HOME_NATIVE_AD_SHOW to d.sudokuHomeNativeAdShow.toString(),
            RemoteConfigKeys.LEVELS_GAME_BANNER_AD_SHOW to d.levelsGameBannerAdShow.toString(),
            RemoteConfigKeys.SUDOKU_GAME_BANNER_AD_SHOW to d.sudokuGameBannerAdShow.toString(),
            RemoteConfigKeys.SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_SHOW to d.sudokuGameCompletedInterstitialAdShow.toString(),
            RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_SHOW to d.levelsGameCompletedInterstitialAdShow.toString(),
            RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY to d.levelsGameCompletedInterstitialAdFrequency.toString(),
            RemoteConfigKeys.SUDOKU_GAME_RESTARTED_INTERSTITIAL_AD_SHOW to d.sudokuGameRestartedInterstitialAdShow.toString(),
            RemoteConfigKeys.LEVELS_GAME_RESTARTED_INTERSTITIAL_AD_SHOW to d.levelsGameRestartedInterstitialAdShow.toString(),
            RemoteConfigKeys.SUDOKU_GAME_BOOSTERS_REWARDED_AD_SHOW to d.sudokuGameBoostersRewardedAdShow.toString(),
            RemoteConfigKeys.LEVELS_GAME_BOOSTERS_REWARDED_AD_SHOW to d.levelsGameBoostersRewardedAdShow.toString(),
            RemoteConfigKeys.LEVELS_GAME_REVIVE_REWARDED_AD_SHOW to d.levelsGameReviveRewardedAdShow.toString(),
            RemoteConfigKeys.SUDOKU_GAME_REVIVE_REWARDED_AD_SHOW to d.sudokuGameReviveRewardedAdShow.toString(),
            RemoteConfigKeys.STREAK_REWARDS_ENABLED to d.streakRewardsEnabled.toString(),
            RemoteConfigKeys.STREAK_REMINDER_ENABLED to d.streakReminderEnabled.toString(),
            RemoteConfigKeys.DAILY_REMINDER_ENABLED to d.dailyReminderEnabled.toString(),
        )
    }

    private fun bool(raw: String?, default: Boolean): Boolean = when (raw?.trim()?.lowercase()) {
        "true", "1", "t", "yes", "y", "on" -> true
        "false", "0", "f", "no", "n", "off" -> false
        else -> default
    }

    /** Out of range = default (not clamped): a typo like 9999 must not silently become a policy value. */
    private fun int(raw: String?, default: Int, range: IntRange): Int {
        val v = raw?.trim()?.toIntOrNull() ?: return default
        return if (v in range) v else default
    }
}
