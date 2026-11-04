package com.tm.meow.sudoku.puzzle.domain.config

import com.tm.meow.sudoku.puzzle.domain.ads.AdFreePolicy
import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicyConfig
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenConfig

/** Remote Config key names. STABLE: they are the contract with the Firebase console. */
object RemoteConfigKeys {
    /** Master ads switch. false always wins over every per-unit switch. */
    const val ADS_SHOW = "ads_show"
    const val AD_FREE_THROUGH_LEVEL = "ad_free_through_level"
    const val LEVELS_HOME_NATIVE_AD_SHOW = "levels_home_native_ad_show"
    const val SUDOKU_HOME_NATIVE_AD_SHOW = "sudoku_home_native_ad_show"
    const val LEVELS_GAME_BANNER_AD_SHOW = "levels_game_banner_ad_show"
    const val SUDOKU_GAME_BANNER_AD_SHOW = "sudoku_game_banner_ad_show"
    const val INTERSTITIAL_SHOW = "interstitial_show"
    const val REWARDED_SHOW = "rewarded_show"
    const val APP_OPEN_SHOW = "app_open_show"
    const val INTERSTITIAL_EVERY_NTH_WIN = "interstitial_every_nth_win"
    const val INTERSTITIAL_MIN_INTERVAL_SEC = "interstitial_min_interval_sec"

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
 */
data class AppRemoteConfig(
    /** Master ads kill switch (gates every format). */
    val adsShow: Boolean = AdPolicyConfig.DEFAULT_ENABLED,
    val adFreeThroughLevel: Int = AdFreePolicy.AD_FREE_THROUGH_LEVEL,
    val levelsHomeNativeAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val sudokuHomeNativeAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val levelsGameBannerAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val sudokuGameBannerAdShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val interstitialShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val rewardedShow: Boolean = AdPolicyConfig.DEFAULT_SHOW,
    val appOpenShow: Boolean = AppOpenConfig.DEFAULT_ENABLED,
    val interstitialEveryNthWin: Int = AdPolicyConfig.DEFAULT_EVERY_NTH_WIN,
    val interstitialMinIntervalSec: Int = (AdPolicyConfig.DEFAULT_MIN_INTERVAL_MS / 1000L).toInt(),
    /** Streak milestone rewards on/off. false hides the reward UI and refuses claims. */
    val streakRewardsEnabled: Boolean = true,
    /** Streak-at-risk evening notification on/off. false = nothing scheduled, nothing shown. */
    val streakReminderEnabled: Boolean = true,
    /** Daily 20:00 notification on/off. false = nothing scheduled, nothing shown. */
    val dailyReminderEnabled: Boolean = true,
) {
    fun toAdPolicyConfig(): AdPolicyConfig = AdPolicyConfig(
        enabled = adsShow,
        adFreeThroughLevel = adFreeThroughLevel,
        everyNthWin = interstitialEveryNthWin,
        minIntervalMs = interstitialMinIntervalSec * 1000L,
        levelsHomeNativeShow = levelsHomeNativeAdShow,
        sudokuHomeNativeShow = sudokuHomeNativeAdShow,
        levelsGameBannerShow = levelsGameBannerAdShow,
        sudokuGameBannerShow = sudokuGameBannerAdShow,
        rewardedShow = rewardedShow,
        interstitialShow = interstitialShow,
    )

    fun toAppOpenConfig(): AppOpenConfig = AppOpenConfig(
        enabled = adsShow && appOpenShow,
        adFreeThroughLevel = adFreeThroughLevel,
    )

    companion object {
        val AD_FREE_LEVEL_RANGE = 0..50
        val EVERY_NTH_WIN_RANGE = 1..10
        val MIN_INTERVAL_SEC_RANGE = 30..600
    }
}

/** Pure parsing of raw string values (blank / missing / malformed / out of range = in-app default). */
object RemoteConfigParser {

    fun parse(read: (String) -> String?): AppRemoteConfig {
        val d = AppRemoteConfig()
        return AppRemoteConfig(
            adsShow = bool(read(RemoteConfigKeys.ADS_SHOW), d.adsShow),
            adFreeThroughLevel = int(
                read(RemoteConfigKeys.AD_FREE_THROUGH_LEVEL), d.adFreeThroughLevel, AppRemoteConfig.AD_FREE_LEVEL_RANGE,
            ),
            levelsHomeNativeAdShow = bool(read(RemoteConfigKeys.LEVELS_HOME_NATIVE_AD_SHOW), d.levelsHomeNativeAdShow),
            sudokuHomeNativeAdShow = bool(read(RemoteConfigKeys.SUDOKU_HOME_NATIVE_AD_SHOW), d.sudokuHomeNativeAdShow),
            levelsGameBannerAdShow = bool(read(RemoteConfigKeys.LEVELS_GAME_BANNER_AD_SHOW), d.levelsGameBannerAdShow),
            sudokuGameBannerAdShow = bool(read(RemoteConfigKeys.SUDOKU_GAME_BANNER_AD_SHOW), d.sudokuGameBannerAdShow),
            interstitialShow = bool(read(RemoteConfigKeys.INTERSTITIAL_SHOW), d.interstitialShow),
            rewardedShow = bool(read(RemoteConfigKeys.REWARDED_SHOW), d.rewardedShow),
            appOpenShow = bool(read(RemoteConfigKeys.APP_OPEN_SHOW), d.appOpenShow),
            interstitialEveryNthWin = int(
                read(RemoteConfigKeys.INTERSTITIAL_EVERY_NTH_WIN), d.interstitialEveryNthWin, AppRemoteConfig.EVERY_NTH_WIN_RANGE,
            ),
            interstitialMinIntervalSec = int(
                read(RemoteConfigKeys.INTERSTITIAL_MIN_INTERVAL_SEC), d.interstitialMinIntervalSec, AppRemoteConfig.MIN_INTERVAL_SEC_RANGE,
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
            RemoteConfigKeys.ADS_SHOW to d.adsShow.toString(),
            RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to d.adFreeThroughLevel.toString(),
            RemoteConfigKeys.LEVELS_HOME_NATIVE_AD_SHOW to d.levelsHomeNativeAdShow.toString(),
            RemoteConfigKeys.SUDOKU_HOME_NATIVE_AD_SHOW to d.sudokuHomeNativeAdShow.toString(),
            RemoteConfigKeys.LEVELS_GAME_BANNER_AD_SHOW to d.levelsGameBannerAdShow.toString(),
            RemoteConfigKeys.SUDOKU_GAME_BANNER_AD_SHOW to d.sudokuGameBannerAdShow.toString(),
            RemoteConfigKeys.INTERSTITIAL_SHOW to d.interstitialShow.toString(),
            RemoteConfigKeys.REWARDED_SHOW to d.rewardedShow.toString(),
            RemoteConfigKeys.APP_OPEN_SHOW to d.appOpenShow.toString(),
            RemoteConfigKeys.INTERSTITIAL_EVERY_NTH_WIN to d.interstitialEveryNthWin.toString(),
            RemoteConfigKeys.INTERSTITIAL_MIN_INTERVAL_SEC to d.interstitialMinIntervalSec.toString(),
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
