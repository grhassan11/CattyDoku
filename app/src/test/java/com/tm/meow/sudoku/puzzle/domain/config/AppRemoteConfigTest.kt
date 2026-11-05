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

    private val boolAdKeys = mapOf(
        RemoteConfigKeys.APP_OPEN_AD_SHOW to "app_open_ad_show",
        RemoteConfigKeys.LEVEL_HOME_NATIVE_AD_SHOW to "level_home_native_ad_show",
        RemoteConfigKeys.SUDOKU_HOME_NATIVE_AD_SHOW to "sudoku_home_native_ad_show",
        RemoteConfigKeys.LEVELS_GAME_BANNER_AD_SHOW to "levels_game_banner_ad_show",
        RemoteConfigKeys.SUDOKU_GAME_BANNER_AD_SHOW to "sudoku_game_banner_ad_show",
        RemoteConfigKeys.SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_SHOW to "sudoku_game_completed_interstitial_ad_show",
        RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_SHOW to "levels_game_completed_interstitial_ad_show",
        RemoteConfigKeys.SUDOKU_GAME_RESTARTED_INTERSTITIAL_AD_SHOW to "sudoku_game_restarted_interstitial_ad_show",
        RemoteConfigKeys.LEVELS_GAME_RESTARTED_INTERSTITIAL_AD_SHOW to "levels_game_restarted_interstitial_ad_show",
        RemoteConfigKeys.SUDOKU_GAME_BOOSTERS_REWARDED_AD_SHOW to "sudoku_game_boosters_rewarded_ad_show",
        RemoteConfigKeys.LEVELS_GAME_BOOSTERS_REWARDED_AD_SHOW to "levels_game_boosters_rewarded_ad_show",
        RemoteConfigKeys.LEVELS_GAME_REVIVE_REWARDED_AD_SHOW to "levels_game_revive_rewarded_ad_show",
        RemoteConfigKeys.SUDOKU_GAME_REVIVE_REWARDED_AD_SHOW to "sudoku_game_revive_rewarded_ad_show",
    )

    @Test
    fun emptyAndBlankGiveInAppDefaults() {
        assertEquals(AppRemoteConfig(), parse())
        assertEquals(
            AppRemoteConfig(),
            parse(
                RemoteConfigKeys.APP_OPEN_AD_SHOW to "",
                RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "  ",
                RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY to " ",
            ),
        )
    }

    @Test
    fun defaultsEqualShippedAdPolicy() {
        val c = AppRemoteConfig()
        assertEquals(AdPolicyConfig(), c.toAdPolicyConfig())
        assertEquals(AppOpenConfig(), c.toAppOpenConfig())
        assertEquals(9, c.adFreeThroughLevel)
        assertEquals(5, c.levelsGameCompletedInterstitialAdFrequency)
        assertEquals(90_000L, c.toAdPolicyConfig().minIntervalMs)
    }

    @Test
    fun defaultsMapRoundTripsToDefaults() {
        val d = RemoteConfigParser.defaults()
        assertEquals(AppRemoteConfig(), RemoteConfigParser.parse { d[it] })
    }

    @Test
    fun adKeysAreStableAndBoolDefaultsTrue() {
        val defaults = RemoteConfigParser.defaults()
        boolAdKeys.forEach { (const, literal) ->
            assertEquals(literal, const)
            assertEquals(literal, "true", defaults[const])
        }
        assertEquals("ad_free_through_level", RemoteConfigKeys.AD_FREE_THROUGH_LEVEL)
        assertEquals("9", defaults[RemoteConfigKeys.AD_FREE_THROUGH_LEVEL])
        assertEquals("levels_game_completed_interstitial_ad_frequency", RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY)
        assertEquals("5", defaults[RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY])
    }

    @Test
    fun removedKeysAreGone() {
        val defaults = RemoteConfigParser.defaults().keys
        listOf(
            "ads_show", "interstitial_show", "rewarded_show", "app_open_show", "levels_home_native_ad_show",
            "interstitial_every_nth_win", "interstitial_min_interval_sec",
        ).forEach { assertFalse(it, it in defaults) }
    }

    @Test
    fun everyBoolAdKeyFalseTurnsOffExactlyItsField() {
        boolAdKeys.keys.forEach { key ->
            val c = parse(key to "false")
            val offCount = listOf(
                c.appOpenAdShow, c.levelHomeNativeAdShow, c.sudokuHomeNativeAdShow, c.levelsGameBannerAdShow,
                c.sudokuGameBannerAdShow, c.sudokuGameCompletedInterstitialAdShow, c.levelsGameCompletedInterstitialAdShow,
                c.sudokuGameRestartedInterstitialAdShow, c.levelsGameRestartedInterstitialAdShow,
                c.sudokuGameBoostersRewardedAdShow, c.levelsGameBoostersRewardedAdShow,
                c.levelsGameReviveRewardedAdShow, c.sudokuGameReviveRewardedAdShow,
            ).count { !it }
            assertEquals(key, 1, offCount)
        }
    }

    @Test
    fun validOverridesApply() {
        val c = parse(
            RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "15",
            RemoteConfigKeys.LEVEL_HOME_NATIVE_AD_SHOW to "FALSE",
            RemoteConfigKeys.SUDOKU_HOME_NATIVE_AD_SHOW to "off",
            RemoteConfigKeys.LEVELS_GAME_BANNER_AD_SHOW to "0",
            RemoteConfigKeys.APP_OPEN_AD_SHOW to "false",
            RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY to "10",
        )
        assertEquals(15, c.adFreeThroughLevel)
        assertFalse(c.levelHomeNativeAdShow)
        assertFalse(c.sudokuHomeNativeAdShow)
        assertFalse(c.levelsGameBannerAdShow)
        assertFalse(c.appOpenAdShow)
        assertEquals(10, c.levelsGameCompletedInterstitialAdFrequency)
    }

    @Test
    fun frequencyBoundsInclusiveAndOutOfRangeFallsBackToDefault() {
        val k = RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY
        assertEquals(1, parse(k to "1").levelsGameCompletedInterstitialAdFrequency)
        assertEquals(50, parse(k to "50").levelsGameCompletedInterstitialAdFrequency)
        for (bad in listOf("0", "-3", "51", "9999", "abc", "2.5", "")) {
            assertEquals(bad, 5, parse(k to bad).levelsGameCompletedInterstitialAdFrequency)
        }
    }

    @Test
    fun malformedAndOutOfRangeFallBackToDefault() {
        val c = parse(
            RemoteConfigKeys.APP_OPEN_AD_SHOW to "maybe",
            RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "9999",
            RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY to "0",
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
    fun eachUnitSwitchBlocksOnlyItsUnit() {
        fun p(key: String) = parse(key to "false").toAdPolicyConfig()
        val d = AdPolicyConfig()
        assertEquals(d.copy(levelsHomeNativeShow = false), p(RemoteConfigKeys.LEVEL_HOME_NATIVE_AD_SHOW))
        assertEquals(d.copy(sudokuHomeNativeShow = false), p(RemoteConfigKeys.SUDOKU_HOME_NATIVE_AD_SHOW))
        assertEquals(d.copy(levelsGameBannerShow = false), p(RemoteConfigKeys.LEVELS_GAME_BANNER_AD_SHOW))
        assertEquals(d.copy(sudokuGameBannerShow = false), p(RemoteConfigKeys.SUDOKU_GAME_BANNER_AD_SHOW))
        assertEquals(d.copy(sudokuWinInterstitialShow = false), p(RemoteConfigKeys.SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_SHOW))
        assertEquals(d.copy(levelsWinInterstitialShow = false), p(RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_SHOW))
        assertEquals(d.copy(sudokuRestartInterstitialShow = false), p(RemoteConfigKeys.SUDOKU_GAME_RESTARTED_INTERSTITIAL_AD_SHOW))
        assertEquals(d.copy(levelsRestartInterstitialShow = false), p(RemoteConfigKeys.LEVELS_GAME_RESTARTED_INTERSTITIAL_AD_SHOW))
        assertEquals(d.copy(sudokuBoostersRewardedShow = false), p(RemoteConfigKeys.SUDOKU_GAME_BOOSTERS_REWARDED_AD_SHOW))
        assertEquals(d.copy(levelsBoostersRewardedShow = false), p(RemoteConfigKeys.LEVELS_GAME_BOOSTERS_REWARDED_AD_SHOW))
        assertEquals(d.copy(levelsReviveRewardedShow = false), p(RemoteConfigKeys.LEVELS_GAME_REVIVE_REWARDED_AD_SHOW))
        assertEquals(d.copy(sudokuReviveRewardedShow = false), p(RemoteConfigKeys.SUDOKU_GAME_REVIVE_REWARDED_AD_SHOW))
        // app-open switch does not touch the AdPolicyConfig
        assertEquals(d, p(RemoteConfigKeys.APP_OPEN_AD_SHOW))
    }

    @Test
    fun appOpenSwitchOnlyFeedsAppOpenConfig() {
        val c = parse(RemoteConfigKeys.APP_OPEN_AD_SHOW to "false")
        assertFalse(c.toAppOpenConfig().enabled)
        assertTrue(c.toAdPolicyConfig().anyInterstitialShow)
        assertTrue(parse().toAppOpenConfig().enabled)
    }

    @Test
    fun configMapsToAdPolicyFields() {
        val p = parse(
            RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "12",
            RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY to "4",
        ).toAdPolicyConfig()
        assertEquals(12, p.adFreeThroughLevel)
        assertEquals(4, p.levelsInterstitialFrequency)
        assertEquals(90_000L, p.minIntervalMs) // hardcoded, not remote
        assertEquals(12, parse(RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "12").toAppOpenConfig().adFreeThroughLevel)
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
        assertEquals(AdPolicyConfig(), c.toAdPolicyConfig())
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
