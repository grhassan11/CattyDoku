package com.tm.meow.sudoku.puzzle.domain.config

import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicyConfig
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
        RemoteConfigKeys.LEVEL_HOME_NATIVE_AD_SHOW to "level_home_native_ad_show",
        RemoteConfigKeys.SUDOKU_HOME_NATIVE_AD_SHOW to "sudoku_home_native_ad_show",
        RemoteConfigKeys.LEVELS_GAME_NATIVE_AD_SHOW to "levels_game_native_ad_show",
        RemoteConfigKeys.SUDOKU_GAME_NATIVE_AD_SHOW to "sudoku_game_native_ad_show",
        RemoteConfigKeys.LEVELS_GAME_COMPLETED_NATIVE_AD_SHOW to "levels_game_completed_native_ad_show",
        RemoteConfigKeys.SUDOKU_GAME_COMPLETED_NATIVE_AD_SHOW to "sudoku_game_completed_native_ad_show",
        RemoteConfigKeys.SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_SHOW to "sudoku_game_completed_interstitial_ad_show",
        RemoteConfigKeys.SUDOKU_GAME_RESTARTED_INTERSTITIAL_AD_SHOW to "sudoku_game_restarted_interstitial_ad_show",
        RemoteConfigKeys.LEVELS_GAME_RESTARTED_INTERSTITIAL_AD_SHOW to "levels_game_restarted_interstitial_ad_show",
        RemoteConfigKeys.LEVELS_GAMEPLAY_BACK_BUTTON_NATIVE_AD_SHOW to "levels_gameplay_back_button_native_ad_show",
        RemoteConfigKeys.SUDOKU_GAME_BACK_INTERSTITIAL_AD_SHOW to "sudoku_game_back_interstitial_ad_show",
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
                RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "  ",
                RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY to " ",
            ),
        )
    }

    @Test
    fun defaultsEqualShippedAdPolicy() {
        val c = AppRemoteConfig()
        assertEquals(AdPolicyConfig(), c.toAdPolicyConfig())
        assertEquals(9, c.adFreeThroughLevel)
        assertEquals(1, c.levelsGameCompletedInterstitialAdFrequency)
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
        assertEquals("1", defaults[RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY])
        assertEquals("sudoku_game_completed_interstitial_ad_frequency", RemoteConfigKeys.SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY)
        assertEquals("1", defaults[RemoteConfigKeys.SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY])
    }

    @Test
    fun sudokuWinFrequencyParsesRangeAndMaps() {
        val k = RemoteConfigKeys.SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY
        assertEquals(10, parse(k to "10").toAdPolicyConfig().sudokuWinInterstitialFrequency)
        assertEquals(50, parse(k to "50").sudokuGameCompletedInterstitialAdFrequency)
        assertEquals(0, parse(k to "0").sudokuGameCompletedInterstitialAdFrequency)
        for (bad in listOf("-3", "51", "abc", "")) {
            assertEquals(bad, 1, parse(k to bad).sudokuGameCompletedInterstitialAdFrequency)
        }
    }

    @Test
    fun sudokuWinFrequencyIsMasterSwitchAndedWithBool() {
        val k = RemoteConfigKeys.SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY
        val b = RemoteConfigKeys.SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_SHOW
        assertFalse(parse(k to "0").toAdPolicyConfig().sudokuWinInterstitialShow)
        assertTrue(parse(k to "5").toAdPolicyConfig().sudokuWinInterstitialShow)
        assertFalse(parse(k to "5", b to "false").toAdPolicyConfig().sudokuWinInterstitialShow)
    }

    @Test
    fun removedKeysAreGone() {
        val defaults = RemoteConfigParser.defaults().keys
        listOf(
            "ads_show", "interstitial_show", "rewarded_show", "app_open_show", "levels_home_native_ad_show",
            "app_open_ad_show", "interstitial_every_nth_win", "interstitial_min_interval_sec",
        ).forEach { assertFalse(it, it in defaults) }
    }

    @Test
    fun everyBoolAdKeyFalseTurnsOffExactlyItsField() {
        boolAdKeys.keys.forEach { key ->
            val c = parse(key to "false")
            val offCount = listOf(
                c.levelHomeNativeAdShow, c.sudokuHomeNativeAdShow, c.levelsGameNativeAdShow,
                c.sudokuGameNativeAdShow, c.levelsCompleteNativeAdShow, c.sudokuCompleteNativeAdShow, c.sudokuGameCompletedInterstitialAdShow,
                c.sudokuGameRestartedInterstitialAdShow, c.levelsGameRestartedInterstitialAdShow,
                c.levelsGameplayBackButtonNativeAdShow, c.sudokuGameBackInterstitialAdShow,
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
            RemoteConfigKeys.LEVELS_GAME_NATIVE_AD_SHOW to "0",
            RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY to "10",
        )
        assertEquals(15, c.adFreeThroughLevel)
        assertFalse(c.levelHomeNativeAdShow)
        assertFalse(c.sudokuHomeNativeAdShow)
        assertFalse(c.levelsGameNativeAdShow)
        assertEquals(10, c.levelsGameCompletedInterstitialAdFrequency)
    }

    @Test
    fun frequencyBoundsInclusiveAndOutOfRangeFallsBackToDefault() {
        val k = RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY
        assertEquals(0, parse(k to "0").levelsGameCompletedInterstitialAdFrequency)
        assertEquals(1, parse(k to "1").levelsGameCompletedInterstitialAdFrequency)
        assertEquals(50, parse(k to "50").levelsGameCompletedInterstitialAdFrequency)
        for (bad in listOf("-3", "51", "9999", "abc", "2.5", "")) {
            assertEquals(bad, 1, parse(k to bad).levelsGameCompletedInterstitialAdFrequency)
        }
    }

    @Test
    fun firstTenListKeyDefaultAndMapping() {
        val k = RemoteConfigKeys.LEVELS_GAME_FIRST_10_LEVELS_COMPLETED_INTERSTITIAL_AD_FREQUENCY
        assertEquals("levels_game_first_10_levels_completed_interstitial_ad_frequency", k)
        assertEquals("2,4,6,8,10", RemoteConfigParser.defaults()[k])
        assertEquals(setOf(2, 4, 6, 8, 10), AppRemoteConfig().levelsGameFirst10LevelsCompletedInterstitialAdLevels)
        assertEquals(setOf(2, 4, 6, 8, 10), parse().toAdPolicyConfig().levelsFirstTenInterstitialLevels)
        assertEquals((1..10).toSet(), parse(k to "1,2,3,4,5,6,7,8,9,10").toAdPolicyConfig().levelsFirstTenInterstitialLevels)
    }

    @Test
    fun firstTenListParsesTrimsDedupesAndIgnoresBadTokens() {
        val k = RemoteConfigKeys.LEVELS_GAME_FIRST_10_LEVELS_COMPLETED_INTERSTITIAL_AD_FREQUENCY
        fun p(v: String) = parse(k to v).levelsGameFirst10LevelsCompletedInterstitialAdLevels
        assertEquals(setOf(1, 3, 5), p(" 1 , 3,5 ,3"))
        assertEquals(setOf(3, 7), p("0,3,abc,11,-2,7,2.5,,"))
        assertEquals(setOf(10), p("10"))
    }

    @Test
    fun firstTenListBlankOrUnparseableFallsBackToDefault() {
        val k = RemoteConfigKeys.LEVELS_GAME_FIRST_10_LEVELS_COMPLETED_INTERSTITIAL_AD_FREQUENCY
        val def = setOf(2, 4, 6, 8, 10)
        for (bad in listOf("", "   ", ",", "abc", "0,11,99", "a,b")) {
            assertEquals(bad, def, parse(k to bad).levelsGameFirst10LevelsCompletedInterstitialAdLevels)
        }
    }

    @Test
    fun malformedAndOutOfRangeFallBackToDefault() {
        val c = parse(
            RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "9999",
            RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY to "51",
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
    fun singleColoredBlockKeyDefaultAndBounds() {
        val k = RemoteConfigKeys.LEVELS_SINGLE_COLORED_BLOCK
        assertEquals("levels_single_colored_block", k)
        assertEquals("50", RemoteConfigParser.defaults()[k])
        assertEquals(50, AppRemoteConfig().levelsSingleColoredBlock)
        assertEquals(0, parse(k to "0").levelsSingleColoredBlock)
        assertEquals(30, parse(k to "30").levelsSingleColoredBlock)
        assertEquals(500, parse(k to "500").levelsSingleColoredBlock)
        for (bad in listOf("-1", "501", "abc", "2.5", "")) {
            assertEquals(bad, 50, parse(k to bad).levelsSingleColoredBlock)
        }
    }

    @Test
    fun eachUnitSwitchBlocksOnlyItsUnit() {
        fun p(key: String) = parse(key to "false").toAdPolicyConfig()
        val d = AdPolicyConfig()
        assertEquals(d.copy(levelsHomeNativeShow = false), p(RemoteConfigKeys.LEVEL_HOME_NATIVE_AD_SHOW))
        assertEquals(d.copy(sudokuHomeNativeShow = false), p(RemoteConfigKeys.SUDOKU_HOME_NATIVE_AD_SHOW))
        assertEquals(d.copy(levelsGameNativeShow = false), p(RemoteConfigKeys.LEVELS_GAME_NATIVE_AD_SHOW))
        assertEquals(d.copy(sudokuGameNativeShow = false), p(RemoteConfigKeys.SUDOKU_GAME_NATIVE_AD_SHOW))
        assertEquals(d.copy(levelsCompleteNativeShow = false), p(RemoteConfigKeys.LEVELS_GAME_COMPLETED_NATIVE_AD_SHOW))
        assertEquals(d.copy(sudokuCompleteNativeShow = false), p(RemoteConfigKeys.SUDOKU_GAME_COMPLETED_NATIVE_AD_SHOW))
        assertEquals(d.copy(sudokuWinInterstitialShow = false), p(RemoteConfigKeys.SUDOKU_GAME_COMPLETED_INTERSTITIAL_AD_SHOW))
        assertEquals(d.copy(sudokuRestartInterstitialShow = false), p(RemoteConfigKeys.SUDOKU_GAME_RESTARTED_INTERSTITIAL_AD_SHOW))
        assertEquals(d.copy(levelsRestartInterstitialShow = false), p(RemoteConfigKeys.LEVELS_GAME_RESTARTED_INTERSTITIAL_AD_SHOW))
        assertEquals(d.copy(levelsBackInterstitialShow = false), p(RemoteConfigKeys.LEVELS_GAMEPLAY_BACK_BUTTON_NATIVE_AD_SHOW))
        assertEquals(d.copy(sudokuBackInterstitialShow = false), p(RemoteConfigKeys.SUDOKU_GAME_BACK_INTERSTITIAL_AD_SHOW))
        assertEquals(d.copy(sudokuBoostersRewardedShow = false), p(RemoteConfigKeys.SUDOKU_GAME_BOOSTERS_REWARDED_AD_SHOW))
        assertEquals(d.copy(levelsBoostersRewardedShow = false), p(RemoteConfigKeys.LEVELS_GAME_BOOSTERS_REWARDED_AD_SHOW))
        assertEquals(d.copy(levelsReviveRewardedShow = false), p(RemoteConfigKeys.LEVELS_GAME_REVIVE_REWARDED_AD_SHOW))
        assertEquals(d.copy(sudokuReviveRewardedShow = false), p(RemoteConfigKeys.SUDOKU_GAME_REVIVE_REWARDED_AD_SHOW))
    }

    @Test
    fun configMapsToAdPolicyFields() {
        val p = parse(
            RemoteConfigKeys.AD_FREE_THROUGH_LEVEL to "12",
            RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY to "4",
        ).toAdPolicyConfig()
        assertEquals(12, p.adFreeThroughLevel)
        assertEquals(4, p.levelsInterstitialFrequency)
    }

    @Test
    fun levelsWinFrequencyIsMasterSwitch() {
        val k = RemoteConfigKeys.LEVELS_GAME_COMPLETED_INTERSTITIAL_AD_FREQUENCY
        assertTrue(parse().toAdPolicyConfig().levelsWinInterstitialShow)
        val off = parse(k to "0").toAdPolicyConfig()
        assertFalse(off.levelsWinInterstitialShow)
        assertEquals(AdPolicyConfig().copy(levelsInterstitialFrequency = 0, levelsWinInterstitialShow = false), off)
        val on = parse(k to "5").toAdPolicyConfig()
        assertTrue(on.levelsWinInterstitialShow)
        assertEquals(5, on.levelsInterstitialFrequency)
    }

    @Test
    fun removedLevelsWinBoolKeyAndOldBackKeyAreGone() {
        val keys = RemoteConfigParser.defaults().keys
        assertFalse("levels_game_completed_interstitial_ad_show" in keys)
        assertFalse("levels_game_back_interstitial_ad_show" in keys)
        assertEquals("levels_gameplay_back_button_native_ad_show", RemoteConfigKeys.LEVELS_GAMEPLAY_BACK_BUTTON_NATIVE_AD_SHOW)
        assertEquals("true", RemoteConfigParser.defaults()[RemoteConfigKeys.LEVELS_GAMEPLAY_BACK_BUTTON_NATIVE_AD_SHOW])
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
    fun splashAppOpenDefaultOnAndKeyIsStable() {
        assertTrue(AppRemoteConfig().splashAppOpenAdShow)
        assertTrue(parse().splashAppOpenAdShow)
        assertFalse(parse(RemoteConfigKeys.SPLASH_APP_OPEN_AD_SHOW to "false").splashAppOpenAdShow)
        assertTrue(parse(RemoteConfigKeys.SPLASH_APP_OPEN_AD_SHOW to "garbage").splashAppOpenAdShow)
        assertEquals("splash_app_open_ad_show", RemoteConfigKeys.SPLASH_APP_OPEN_AD_SHOW)
        assertEquals("true", RemoteConfigParser.defaults()[RemoteConfigKeys.SPLASH_APP_OPEN_AD_SHOW])
        assertEquals(AdPolicyConfig(), parse(RemoteConfigKeys.SPLASH_APP_OPEN_AD_SHOW to "false").toAdPolicyConfig())
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
