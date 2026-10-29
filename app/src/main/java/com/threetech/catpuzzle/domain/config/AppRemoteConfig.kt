package com.threetech.catpuzzle.domain.config

import com.threetech.catpuzzle.domain.ads.AdFreePolicy
import com.threetech.catpuzzle.domain.ads.AdPolicyConfig
import com.threetech.catpuzzle.domain.ads.AppOpenConfig

/** Remote Config key names. STABLE: they are the contract with the Firebase console. */
object RemoteConfigKeys {
    const val ADS_ENABLED = "ads_enabled"
    const val AD_FREE_THROUGH_LEVEL = "ad_free_through_level"
    const val NATIVE_HOME_ENABLED = "native_home_enabled"
    const val BANNER_GAME_ENABLED = "banner_game_enabled"
    const val REWARDED_ENABLED = "rewarded_enabled"
    const val APP_OPEN_ENABLED = "app_open_enabled"
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
    val adsEnabled: Boolean = AdPolicyConfig.DEFAULT_ENABLED,
    val adFreeThroughLevel: Int = AdFreePolicy.AD_FREE_THROUGH_LEVEL,
    val nativeHomeEnabled: Boolean = AdPolicyConfig.DEFAULT_NATIVE_HOME_ENABLED,
    val bannerGameEnabled: Boolean = AdPolicyConfig.DEFAULT_BANNER_GAME_ENABLED,
    val rewardedEnabled: Boolean = AdPolicyConfig.DEFAULT_REWARDED_ENABLED,
    val appOpenEnabled: Boolean = AppOpenConfig.DEFAULT_ENABLED,
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
        enabled = adsEnabled,
        adFreeThroughLevel = adFreeThroughLevel,
        everyNthWin = interstitialEveryNthWin,
        minIntervalMs = interstitialMinIntervalSec * 1000L,
        nativeHomeEnabled = nativeHomeEnabled,
        bannerGameEnabled = bannerGameEnabled,
        rewardedEnabled = rewardedEnabled,
    )

    fun toAppOpenConfig(): AppOpenConfig = AppOpenConfig(
        enabled = adsEnabled && appOpenEnabled,
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
            adsEnabled = bool(read(RemoteConfigKeys.ADS_ENABLED), d.adsEnabled),
            adFreeThroughLevel = int(
                read(RemoteConfigKeys.AD_FREE_THROUGH_LEVEL), d.adFreeThroughLevel, AppRemoteConfig.AD_FREE_LEVEL_RANGE,
            ),
            nativeHomeEnabled = bool(read(RemoteConfigKeys.NATIVE_HOME_ENABLED), d.nativeHomeEnabled),
            bannerGameEnabled = bool(read(RemoteConfigKeys.BANNER_GAME_ENABLED), d.bannerGameEnabled),
            rewardedEnabled = bool(read(RemoteConfigKeys.REWARDED_ENABLED), d.rewardedEnabled),
            appOpenEnabled = bool(read(RemoteConfigKeys.APP_OPEN_ENABLED), d.appOpenEnabled),
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
            RemoteConfigKeys.ADS_ENABLED to d.adsEnabled.toString(),
            RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to d.adFreeThroughLevel.toString(),
            RemoteConfigKeys.NATIVE_HOME_ENABLED to d.nativeHomeEnabled.toString(),
            RemoteConfigKeys.BANNER_GAME_ENABLED to d.bannerGameEnabled.toString(),
            RemoteConfigKeys.REWARDED_ENABLED to d.rewardedEnabled.toString(),
            RemoteConfigKeys.APP_OPEN_ENABLED to d.appOpenEnabled.toString(),
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
