package com.tm.meow.sudoku.puzzle.domain.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialPolicyTest {

    private val cfg = AdPolicyConfig()

    /** A context that satisfies EVERY rule (level 20 is a multiple of the default frequency 5); each test breaks exactly one. */
    private fun ok(
        trigger: AdTrigger = AdTrigger.LEVEL_WON_CONTINUE,
        canRequestAds: Boolean = true,
        level: Int = 20,
        isDaily: Boolean = false,
        inTutorial: Boolean = false,
        now: Long = 1_000_000L,
        sessionStart: Long = 0L,
        lastAd: Long? = null,
        lastLevel: Int = 0,
        shown: Int = 0,
    ) = InterstitialContext(
        trigger = trigger,
        canRequestAds = canRequestAds,
        level = level,
        isDaily = isDaily,
        inTutorial = inTutorial,
        nowMs = now,
        sessionStartMs = sessionStart,
        lastAdShownMs = lastAd,
        lastInterstitialLevel = lastLevel,
        adsShownThisSession = shown,
    )

    private fun skip(reason: AdSkipReason) = AdDecision.Skip(reason)

    private fun winAt(level: Int, config: AdPolicyConfig, lastLevel: Int = 0) =
        InterstitialPolicy.decide(ok(level = level, lastLevel = lastLevel), config)

    @Test
    fun allRulesMetShows() {
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(), cfg))
    }

    @Test
    fun defaultsAreTheDocumentedValues() {
        assertEquals(9, cfg.adFreeThroughLevel)
        assertEquals(10, AdFreePolicy.FIRST_AD_LEVEL)
        assertEquals(5, cfg.levelsInterstitialFrequency)
        assertEquals(90_000L, cfg.minIntervalMs)
        assertEquals(120_000L, cfg.minSessionAgeMs)
        assertEquals(6, cfg.maxPerSession)
        assertEquals(3_000L, cfg.loadWaitTimeoutMs)
        assertTrue(cfg.levelsWinInterstitialShow && cfg.sudokuWinInterstitialShow)
        assertTrue(cfg.levelsRestartInterstitialShow && cfg.sudokuRestartInterstitialShow)
    }

    @Test
    fun sudokuTriggersIgnoreQueensPacingButKeepConsentSwitchAndRemoveAds() {
        for (t in listOf(AdTrigger.SUDOKU_WON_CONTINUE, AdTrigger.SUDOKU_RESTART)) {
            // Queens ad-free level, frequency, level-already-had-ad, session age, interval and cap do not apply
            assertEquals("$t", AdDecision.Show, InterstitialPolicy.decide(ok(trigger = t, level = 0, lastLevel = 99), cfg))
            assertEquals("$t", AdDecision.Show, InterstitialPolicy.decide(ok(trigger = t, level = 7), cfg))
            assertEquals("$t", AdDecision.Show, InterstitialPolicy.decide(ok(trigger = t, now = 1L), cfg))
            assertEquals("$t", AdDecision.Show, InterstitialPolicy.decide(ok(trigger = t, lastAd = 999_999L), cfg))
            assertEquals("$t", AdDecision.Show, InterstitialPolicy.decide(ok(trigger = t, shown = cfg.maxPerSession), cfg))
            assertEquals("$t", skip(AdSkipReason.NO_CONSENT), InterstitialPolicy.decide(ok(trigger = t, canRequestAds = false), cfg))
        }
    }

    @Test
    fun sudokuWinSwitchGatesOnlySudokuWin() {
        val off = cfg.copy(sudokuWinInterstitialShow = false)
        assertEquals(skip(AdSkipReason.DISABLED), InterstitialPolicy.decide(ok(trigger = AdTrigger.SUDOKU_WON_CONTINUE), off))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(trigger = AdTrigger.SUDOKU_RESTART), off))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(trigger = AdTrigger.LEVEL_WON_CONTINUE), off))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(trigger = AdTrigger.LOSE_RETRY), off))
    }

    @Test
    fun sudokuRestartSwitchGatesOnlySudokuRestart() {
        val off = cfg.copy(sudokuRestartInterstitialShow = false)
        assertEquals(skip(AdSkipReason.DISABLED), InterstitialPolicy.decide(ok(trigger = AdTrigger.SUDOKU_RESTART), off))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(trigger = AdTrigger.SUDOKU_WON_CONTINUE), off))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(trigger = AdTrigger.LEVEL_WON_CONTINUE), off))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(trigger = AdTrigger.LOSE_RETRY), off))
    }

    @Test
    fun levelsWinSwitchGatesOnlyLevelsWin() {
        val off = cfg.copy(levelsWinInterstitialShow = false)
        assertEquals(skip(AdSkipReason.DISABLED), InterstitialPolicy.decide(ok(trigger = AdTrigger.LEVEL_WON_CONTINUE), off))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(trigger = AdTrigger.LOSE_RETRY), off))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(trigger = AdTrigger.SUDOKU_WON_CONTINUE), off))
    }

    @Test
    fun levelsRetrySwitchGatesOnlyRetry() {
        val off = cfg.copy(levelsRestartInterstitialShow = false)
        assertEquals(skip(AdSkipReason.DISABLED), InterstitialPolicy.decide(ok(trigger = AdTrigger.LOSE_RETRY), off))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(trigger = AdTrigger.LEVEL_WON_CONTINUE), off))
    }

    @Test
    fun allInterstitialSwitchesOffDisablesEveryBoundaryAndDisablesPreload() {
        val off = cfg.copy(
            levelsWinInterstitialShow = false, sudokuWinInterstitialShow = false,
            levelsRestartInterstitialShow = false, sudokuRestartInterstitialShow = false,
        )
        for (t in listOf(AdTrigger.LEVEL_WON_CONTINUE, AdTrigger.SUDOKU_WON_CONTINUE, AdTrigger.SUDOKU_RESTART, AdTrigger.LOSE_RETRY)) {
            assertEquals("$t", skip(AdSkipReason.DISABLED), InterstitialPolicy.decide(ok(trigger = t), off))
        }
        assertEquals(false, off.anyInterstitialShow)
        assertEquals(true, cfg.anyInterstitialShow)
        assertEquals(true, cfg.copy(levelsWinInterstitialShow = false, sudokuWinInterstitialShow = false, levelsRestartInterstitialShow = false).anyInterstitialShow)
        // other switches do not affect interstitial
        val others = cfg.copy(
            levelsBoostersRewardedShow = false, sudokuBoostersRewardedShow = false, levelsReviveRewardedShow = false,
            sudokuReviveRewardedShow = false, levelsGameBannerShow = false, levelsHomeNativeShow = false, sudokuHomeNativeShow = false,
        )
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(), others))
    }

    @Test
    fun noConsentNeverShows() {
        assertEquals(skip(AdSkipReason.NO_CONSENT), InterstitialPolicy.decide(ok(canRequestAds = false), cfg))
    }

    @Test
    fun adFreeThroughLevelNineThenAdsFromTen() {
        val every = cfg.copy(levelsInterstitialFrequency = 1)
        for (l in 1..9) assertEquals("L$l", skip(AdSkipReason.AD_FREE_LEVEL), InterstitialPolicy.decide(ok(level = l), every))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(level = 10), every))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(level = 40), every))
    }

    @Test
    fun frequencyOneShowsOnTenElevenTwelve() {
        val c = cfg.copy(levelsInterstitialFrequency = 1)
        for (l in listOf(10, 11, 12, 13, 25)) assertEquals("L$l", AdDecision.Show, winAt(l, c, lastLevel = l - 1))
    }

    @Test
    fun frequencyFiveShowsOnTenFifteenTwenty() {
        val c = cfg.copy(levelsInterstitialFrequency = 5)
        for (l in listOf(10, 15, 20, 25)) assertEquals("L$l", AdDecision.Show, winAt(l, c))
        for (l in listOf(11, 12, 13, 14, 16, 19, 21)) {
            assertEquals("L$l", skip(AdSkipReason.LEVEL_NOT_ON_FREQUENCY), winAt(l, c))
        }
    }

    @Test
    fun frequencyTenShowsOnTenTwentyThirty() {
        val c = cfg.copy(levelsInterstitialFrequency = 10)
        for (l in listOf(10, 20, 30)) assertEquals("L$l", AdDecision.Show, winAt(l, c))
        for (l in listOf(11, 15, 25, 29)) assertEquals("L$l", skip(AdSkipReason.LEVEL_NOT_ON_FREQUENCY), winAt(l, c))
    }

    @Test
    fun levelNineNeverShowsAtAnyFrequency() {
        for (n in listOf(1, 3, 5, 9, 10)) {
            assertEquals("N$n", skip(AdSkipReason.AD_FREE_LEVEL), winAt(9, cfg.copy(levelsInterstitialFrequency = n)))
        }
    }

    @Test
    fun noFirstEverExemption() {
        // lastLevel == 0 (no ad ever): a non-multiple level is still skipped, even the first non-ad-free one.
        assertEquals(skip(AdSkipReason.LEVEL_NOT_ON_FREQUENCY), winAt(11, cfg.copy(levelsInterstitialFrequency = 5), lastLevel = 0))
        assertEquals(skip(AdSkipReason.LEVEL_NOT_ON_FREQUENCY), winAt(10, cfg.copy(levelsInterstitialFrequency = 3), lastLevel = 0))
    }

    @Test
    fun nonPositiveFrequencyIsTreatedAsEveryLevel() {
        assertEquals(AdDecision.Show, winAt(11, cfg.copy(levelsInterstitialFrequency = 0)))
    }

    @Test
    fun frequencyKeepsOtherGates() {
        assertEquals(skip(AdSkipReason.SESSION_TOO_YOUNG), InterstitialPolicy.decide(ok(level = 10, now = 60_000L), cfg))
        assertEquals(skip(AdSkipReason.NO_CONSENT), InterstitialPolicy.decide(ok(level = 10, canRequestAds = false), cfg))
        assertEquals(skip(AdSkipReason.DAILY), InterstitialPolicy.decide(ok(level = 10, isDaily = true), cfg))
        assertEquals(skip(AdSkipReason.SESSION_CAP), InterstitialPolicy.decide(ok(level = 10, shown = cfg.maxPerSession), cfg))
        assertEquals(skip(AdSkipReason.TOO_SOON_AFTER_LAST_AD), InterstitialPolicy.decide(ok(level = 10, lastAd = 1_000_000L - 10L), cfg))
    }

    @Test
    fun sessionAgeBoundary() {
        assertEquals(skip(AdSkipReason.SESSION_TOO_YOUNG), InterstitialPolicy.decide(ok(now = 119_999L), cfg))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(now = 120_000L), cfg))
    }

    @Test
    fun minIntervalSinceLastAdBoundary() {
        val now = 1_000_000L
        assertEquals(skip(AdSkipReason.TOO_SOON_AFTER_LAST_AD), InterstitialPolicy.decide(ok(now = now, lastAd = now - 89_999L), cfg))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(now = now, lastAd = now - 90_000L), cfg))
    }

    @Test
    fun levelThatAlreadyHadAnAdDoesNotRepeat() {
        assertEquals(skip(AdSkipReason.LEVEL_ALREADY_HAD_AD), InterstitialPolicy.decide(ok(level = 20, lastLevel = 20), cfg))
        assertEquals(skip(AdSkipReason.LEVEL_ALREADY_HAD_AD), InterstitialPolicy.decide(ok(level = 20, lastLevel = 25), cfg))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(level = 25, lastLevel = 20), cfg))
    }

    @Test
    fun sessionCap() {
        assertEquals(skip(AdSkipReason.SESSION_CAP), InterstitialPolicy.decide(ok(shown = cfg.maxPerSession), cfg))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(shown = cfg.maxPerSession - 1), cfg))
    }

    @Test
    fun tutorialNeverShows() {
        assertEquals(skip(AdSkipReason.TUTORIAL), InterstitialPolicy.decide(ok(inTutorial = true), cfg))
        assertEquals(skip(AdSkipReason.TUTORIAL), InterstitialPolicy.decide(ok(trigger = AdTrigger.TUTORIAL), cfg))
    }

    @Test
    fun dailyNeverShows() {
        assertEquals(skip(AdSkipReason.DAILY), InterstitialPolicy.decide(ok(isDaily = true), cfg))
    }

    @Test
    fun nonBoundaryTriggersNeverShowEvenWhenEverythingElseAllows() {
        for (t in listOf(AdTrigger.DAILY_START, AdTrigger.APP_LAUNCH, AdTrigger.APP_RESUME, AdTrigger.MID_GAMEPLAY)) {
            assertEquals("$t", skip(AdSkipReason.NOT_A_BOUNDARY), InterstitialPolicy.decide(ok(trigger = t), cfg))
        }
    }

    @Test
    fun loseRetryIsOnByDefaultAndIgnoresFrequency() {
        // level 11 is not a multiple of 5: a win would be skipped, Retry still shows.
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(trigger = AdTrigger.LOSE_RETRY, level = 11), cfg))
        assertEquals(skip(AdSkipReason.LEVEL_NOT_ON_FREQUENCY), winAt(11, cfg))
    }

    @Test
    fun loseRetryKeepsConsentLevelIntervalSessionAgeAndCap() {
        assertEquals(skip(AdSkipReason.NO_CONSENT), InterstitialPolicy.decide(ok(trigger = AdTrigger.LOSE_RETRY, canRequestAds = false), cfg))
        assertEquals(skip(AdSkipReason.AD_FREE_LEVEL), InterstitialPolicy.decide(ok(trigger = AdTrigger.LOSE_RETRY, level = 9), cfg))
        assertEquals(
            skip(AdSkipReason.TOO_SOON_AFTER_LAST_AD),
            InterstitialPolicy.decide(ok(trigger = AdTrigger.LOSE_RETRY, lastAd = 1_000_000L - 10L), cfg),
        )
        assertEquals(skip(AdSkipReason.SESSION_TOO_YOUNG), InterstitialPolicy.decide(ok(trigger = AdTrigger.LOSE_RETRY, now = 60_000L), cfg))
        assertEquals(skip(AdSkipReason.SESSION_CAP), InterstitialPolicy.decide(ok(trigger = AdTrigger.LOSE_RETRY, shown = cfg.maxPerSession), cfg))
    }

    @Test
    fun consentCheckedBeforeAnythingElseThatWouldAlsoSkip() {
        // level 1 + no consent: consent reason wins (ads must never even be considered without it)
        assertEquals(skip(AdSkipReason.NO_CONSENT), InterstitialPolicy.decide(ok(canRequestAds = false, level = 1), cfg))
    }

    @Test
    fun matrixShowsOnlyWhenEveryRuleHolds() {
        var shows = 0
        for (consent in listOf(true, false)) for (tutorial in listOf(true, false)) for (daily in listOf(true, false)) {
            for (level in listOf(1, 3, 9, 10, 11, 15, 40)) for (age in listOf(60_000L, 200_000L)) {
                val d = InterstitialPolicy.decide(
                    ok(canRequestAds = consent, inTutorial = tutorial, isDaily = daily, level = level, now = age, lastLevel = 1), cfg,
                )
                val expected = consent && !tutorial && !daily && level >= 10 && level % 5 == 0 && age >= 120_000L
                assertEquals("c=$consent t=$tutorial d=$daily l=$level a=$age", expected, d == AdDecision.Show)
                if (d == AdDecision.Show) shows++
            }
        }
        assertTrue(shows > 0)
    }
}
