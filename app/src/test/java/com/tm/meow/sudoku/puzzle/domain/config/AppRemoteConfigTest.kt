package com.tm.meow.sudoku.puzzle.domain.config

import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicyConfig
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenConfig
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
        assertEquals(AppRemoteConfig(), parse(RemoteConfigKeys.ADS_SHOW to "", RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "  "))
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
            RemoteConfigKeys.ADS_SHOW to "false",
            RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "15",
            RemoteConfigKeys.LEVELS_HOME_NATIVE_AD_SHOW to "FALSE",
            RemoteConfigKeys.SUDOKU_HOME_NATIVE_AD_SHOW to "off",
            RemoteConfigKeys.LEVELS_GAME_BANNER_AD_SHOW to "0",
            RemoteConfigKeys.INTERSTITIAL_SHOW to "false",
            RemoteConfigKeys.REWARDED_SHOW to "false",
            RemoteConfigKeys.APP_OPEN_SHOW to "false",
            RemoteConfigKeys.INTERSTITIAL_EVERY_NTH_WIN to "3",
            RemoteConfigKeys.INTERSTITIAL_MIN_INTERVAL_SEC to "120",
        )
        assertFalse(c.adsShow)
        assertEquals(15, c.adFreeThroughLevel)
        assertFalse(c.levelsHomeNativeAdShow)
        assertFalse(c.sudokuHomeNativeAdShow)
        assertFalse(c.levelsGameBannerAdShow)
        assertFalse(c.interstitialShow)
        assertFalse(c.rewardedShow)
        assertFalse(c.appOpenShow)
        assertEquals(3, c.interstitialEveryNthWin)
        assertEquals(120, c.interstitialMinIntervalSec)
    }

    @Test
    fun malformedAndOutOfRangeFallBackToDefault() {
        val c = parse(
            RemoteConfigKeys.ADS_SHOW to "maybe",
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
    fun showKeysAreStableAndDefaultTrue() {
        val keys = mapOf(
            RemoteConfigKeys.ADS_SHOW to "ads_show",
            RemoteConfigKeys.LEVELS_HOME_NATIVE_AD_SHOW to "levels_home_native_ad_show",
            RemoteConfigKeys.SUDOKU_HOME_NATIVE_AD_SHOW to "sudoku_home_native_ad_show",
            RemoteConfigKeys.LEVELS_GAME_BANNER_AD_SHOW to "levels_game_banner_ad_show",
            RemoteConfigKeys.SUDOKU_GAME_BANNER_AD_SHOW to "sudoku_game_banner_ad_show",
            RemoteConfigKeys.INTERSTITIAL_SHOW to "interstitial_show",
            RemoteConfigKeys.REWARDED_SHOW to "rewarded_show",
            RemoteConfigKeys.APP_OPEN_SHOW to "app_open_show",
        )
        val defaults = RemoteConfigParser.defaults()
        keys.forEach { (const, literal) ->
            assertEquals(literal, const)
            assertEquals("true", defaults[const])
        }
        val c = parse()
        assertTrue(c.adsShow && c.levelsHomeNativeAdShow && c.sudokuHomeNativeAdShow && c.levelsGameBannerAdShow)
        assertTrue(c.sudokuGameBannerAdShow)
        assertTrue(c.interstitialShow && c.rewardedShow && c.appOpenShow)
    }

    @Test
    fun removedKeysAreGone() {
        val defaults = RemoteConfigParser.defaults().keys
        listOf("ads_enabled", "rewarded_enabled", "app_open_enabled", "native_home_enabled", "banner_game_enabled")
            .forEach { assertFalse(it in defaults) }
    }

    @Test
    fun eachUnitSwitchBlocksOnlyItsUnit() {
        fun p(key: String) = parse(key to "false").toAdPolicyConfig()
        val levelsNative = p(RemoteConfigKeys.LEVELS_HOME_NATIVE_AD_SHOW)
        assertFalse(levelsNative.levelsHomeNativeShow)
        assertEquals(AdPolicyConfig().copy(levelsHomeNativeShow = false), levelsNative)
        assertEquals(AdPolicyConfig().copy(sudokuHomeNativeShow = false), p(RemoteConfigKeys.SUDOKU_HOME_NATIVE_AD_SHOW))
        assertEquals(AdPolicyConfig().copy(levelsGameBannerShow = false), p(RemoteConfigKeys.LEVELS_GAME_BANNER_AD_SHOW))
        assertEquals(AdPolicyConfig().copy(sudokuGameBannerShow = false), p(RemoteConfigKeys.SUDOKU_GAME_BANNER_AD_SHOW))
        assertEquals(AdPolicyConfig().copy(interstitialShow = false), p(RemoteConfigKeys.INTERSTITIAL_SHOW))
        assertEquals(AdPolicyConfig().copy(rewardedShow = false), p(RemoteConfigKeys.REWARDED_SHOW))
        // app-open switch does not touch the AdPolicyConfig
        assertEquals(AdPolicyConfig(), p(RemoteConfigKeys.APP_OPEN_SHOW))
    }

    @Test
    fun masterKillSwitchDisablesAppOpenAndFeedsAdPolicy() {
        val c = parse(RemoteConfigKeys.ADS_SHOW to "false")
        assertFalse(c.toAdPolicyConfig().enabled)
        assertFalse(c.toAppOpenConfig().enabled)
    }

    @Test
    fun appOpenOnlySwitchLeavesOtherFormats() {
        val c = parse(RemoteConfigKeys.APP_OPEN_SHOW to "false")
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
