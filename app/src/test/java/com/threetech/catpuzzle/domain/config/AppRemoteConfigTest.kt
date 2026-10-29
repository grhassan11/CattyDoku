package com.threetech.catpuzzle.domain.config

import com.threetech.catpuzzle.domain.ads.AdPolicyConfig
import com.threetech.catpuzzle.domain.ads.AppOpenConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRemoteConfigTest {

    private fun parse(vararg pairs: Pair<String, String>): AppRemoteConfig {
        val m = mapOf(*pairs)
        return RemoteConfigParser.parse { m[it] }
    }

    @Test
    fun emptyAndBlankGiveInAppDefaults() {
        assertEquals(AppRemoteConfig(), parse())
        assertEquals(AppRemoteConfig(), parse(RemoteConfigKeys.ADS_ENABLED to "", RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "  "))
    }

    @Test
    fun defaultsEqualShippedAdPolicy() {
        val c = AppRemoteConfig()
        assertEquals(AdPolicyConfig(), c.toAdPolicyConfig())
        assertEquals(AppOpenConfig(), c.toAppOpenConfig())
        assertEquals(9, c.adFreeThroughLevel)
    }

    @Test
    fun defaultsMapRoundTripsToDefaults() {
        val d = RemoteConfigParser.defaults()
        assertEquals(AppRemoteConfig(), RemoteConfigParser.parse { d[it] })
    }

    @Test
    fun validOverridesApply() {
        val c = parse(
            RemoteConfigKeys.ADS_ENABLED to "false",
            RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "15",
            RemoteConfigKeys.NATIVE_HOME_ENABLED to "FALSE",
            RemoteConfigKeys.BANNER_GAME_ENABLED to "0",
            RemoteConfigKeys.REWARDED_ENABLED to "false",
            RemoteConfigKeys.APP_OPEN_ENABLED to "false",
            RemoteConfigKeys.INTERSTITIAL_EVERY_NTH_WIN to "3",
            RemoteConfigKeys.INTERSTITIAL_MIN_INTERVAL_SEC to "120",
        )
        assertFalse(c.adsEnabled)
        assertEquals(15, c.adFreeThroughLevel)
        assertFalse(c.nativeHomeEnabled)
        assertFalse(c.bannerGameEnabled)
        assertFalse(c.rewardedEnabled)
        assertFalse(c.appOpenEnabled)
        assertEquals(3, c.interstitialEveryNthWin)
        assertEquals(120, c.interstitialMinIntervalSec)
    }

    @Test
    fun malformedAndOutOfRangeFallBackToDefault() {
        val c = parse(
            RemoteConfigKeys.ADS_ENABLED to "maybe",
            RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "9999",
            RemoteConfigKeys.INTERSTITIAL_EVERY_NTH_WIN to "0",
            RemoteConfigKeys.INTERSTITIAL_MIN_INTERVAL_SEC to "abc",
        )
        assertEquals(AppRemoteConfig(), c)
    }

    @Test
    fun adFreeLevelBoundsAreInclusive() {
        assertEquals(0, parse(RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "0").adFreeThroughLevel)
        assertEquals(50, parse(RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "50").adFreeThroughLevel)
        assertEquals(9, parse(RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "-1").adFreeThroughLevel)
    }

    @Test
    fun masterKillSwitchDisablesAppOpenAndFeedsAdPolicy() {
        val c = parse(RemoteConfigKeys.ADS_ENABLED to "false")
        assertFalse(c.toAdPolicyConfig().enabled)
        assertFalse(c.toAppOpenConfig().enabled)
    }

    @Test
    fun appOpenOnlySwitchLeavesOtherFormats() {
        val c = parse(RemoteConfigKeys.APP_OPEN_ENABLED to "false")
        assertFalse(c.toAppOpenConfig().enabled)
        assertTrue(c.toAdPolicyConfig().enabled)
    }

    @Test
    fun configMapsToAdPolicyFields() {
        val p = parse(
            RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "12",
            RemoteConfigKeys.INTERSTITIAL_EVERY_NTH_WIN to "4",
            RemoteConfigKeys.INTERSTITIAL_MIN_INTERVAL_SEC to "60",
        ).toAdPolicyConfig()
        assertEquals(12, p.adFreeThroughLevel)
        assertEquals(4, p.everyNthWin)
        assertEquals(60_000L, p.minIntervalMs)
    }

    @Test
    fun streakRewardsDefaultOnAndKeyIsStable() {
        assertTrue(AppRemoteConfig().streakRewardsEnabled)
        assertTrue(parse().streakRewardsEnabled)
        assertEquals("streak_rewards_enabled", RemoteConfigKeys.STREAK_REWARDS_ENABLED)
        assertEquals("true", RemoteConfigParser.defaults()[RemoteConfigKeys.STREAK_REWARDS_ENABLED])
    }

    @Test
    fun streakReminderDefaultOnAndKeyIsStable() {
        assertTrue(AppRemoteConfig().streakReminderEnabled)
        assertTrue(parse().streakReminderEnabled)
        assertFalse(parse(RemoteConfigKeys.STREAK_REMINDER_ENABLED to "false").streakReminderEnabled)
        assertEquals("streak_reminder_enabled", RemoteConfigKeys.STREAK_REMINDER_ENABLED)
        assertEquals("true", RemoteConfigParser.defaults()[RemoteConfigKeys.STREAK_REMINDER_ENABLED])
    }

    @Test
    fun dailyReminderDefaultOnAndKeyIsStable() {
        assertTrue(AppRemoteConfig().dailyReminderEnabled)
        assertFalse(parse(RemoteConfigKeys.DAILY_REMINDER_ENABLED to "false").dailyReminderEnabled)
        assertEquals("daily_reminder_enabled", RemoteConfigKeys.DAILY_REMINDER_ENABLED)
        assertEquals("true", RemoteConfigParser.defaults()[RemoteConfigKeys.DAILY_REMINDER_ENABLED])
    }

    @Test
    fun streakRewardsKillSwitchOnlyAffectsItself() {
        val c = parse(RemoteConfigKeys.STREAK_REWARDS_ENABLED to "false")
        assertFalse(c.streakRewardsEnabled)
        assertEquals(AppRemoteConfig().copy(streakRewardsEnabled = false), c)
        assertTrue(c.toAdPolicyConfig().enabled)
    }

    @Test
    fun streakRewardsMalformedFallsBackToOn() {
        assertTrue(parse(RemoteConfigKeys.STREAK_REWARDS_ENABLED to "maybe").streakRewardsEnabled)
        assertTrue(parse(RemoteConfigKeys.STREAK_REWARDS_ENABLED to "").streakRewardsEnabled)
        assertFalse(parse(RemoteConfigKeys.STREAK_REWARDS_ENABLED to "0").streakRewardsEnabled)
    }

    @Test
    fun keysAreSnakeCase() {
        val snake = Regex("^[a-z][a-z0-9_]*$")
        assertTrue(RemoteConfigParser.defaults().keys.all { snake.matches(it) })
    }
}
