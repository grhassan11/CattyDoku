package com.threetech.catpuzzle.domain.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialPolicyTest {

    private val cfg = AdPolicyConfig()

    /** A context that satisfies EVERY rule; each test breaks exactly one. */
    private fun ok(
        trigger: AdTrigger = AdTrigger.LEVEL_WON_CONTINUE,
        canRequestAds: Boolean = true,
        level: Int = 16,
        isDaily: Boolean = false,
        inTutorial: Boolean = false,
        wins: Int = 2,
        now: Long = 1_000_000L,
        sessionStart: Long = 0L,
        lastAd: Long? = null,
        lastLevel: Int = 0,
        shown: Int = 0,
    ) = InterstitialContext(
        trigger, canRequestAds, level, isDaily, inTutorial, wins, now, sessionStart, lastAd, lastLevel, shown,
    )

    private fun skip(reason: AdSkipReason) = AdDecision.Skip(reason)

    @Test
    fun allRulesMetShows() {
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(), cfg))
    }

    @Test
    fun defaultsAreTheDocumentedValues() {
        assertEquals(9, cfg.adFreeThroughLevel)
        assertEquals(10, AdFreePolicy.FIRST_AD_LEVEL)
        assertEquals(2, cfg.everyNthWin)
        assertEquals(90_000L, cfg.minIntervalMs)
        assertEquals(120_000L, cfg.minSessionAgeMs)
        assertEquals(3_000L, cfg.loadWaitTimeoutMs)
        assertEquals(false, cfg.showOnLoseRetry)
    }

    @Test
    fun noConsentNeverShows() {
        assertEquals(skip(AdSkipReason.NO_CONSENT), InterstitialPolicy.decide(ok(canRequestAds = false), cfg))
    }

    @Test
    fun disabledKillSwitch() {
        assertEquals(skip(AdSkipReason.DISABLED), InterstitialPolicy.decide(ok(), cfg.copy(enabled = false)))
    }

    @Test
    fun adFreeThroughLevelNineThenAdsFromTen() {
        for (l in 1..9) assertEquals("L$l", skip(AdSkipReason.AD_FREE_LEVEL), InterstitialPolicy.decide(ok(level = l), cfg))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(level = 10), cfg))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(level = 40), cfg))
    }

    @Test
    fun everyNthWinCounts() {
        // lastLevel > 0 = an interstitial was shown before, so the Nth-win rule applies.
        assertEquals(skip(AdSkipReason.NOT_NTH_WIN), InterstitialPolicy.decide(ok(wins = 0, lastLevel = 12), cfg))
        assertEquals(skip(AdSkipReason.NOT_NTH_WIN), InterstitialPolicy.decide(ok(wins = 1, lastLevel = 12), cfg))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(wins = 2, lastLevel = 12), cfg))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(wins = 5, lastLevel = 12), cfg)) // missed ones accumulate
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(wins = 1, lastLevel = 12), cfg.copy(everyNthWin = 1)))
        assertEquals(skip(AdSkipReason.NOT_NTH_WIN), InterstitialPolicy.decide(ok(wins = 2, lastLevel = 12), cfg.copy(everyNthWin = 3)))
    }

    @Test
    fun firstEverInterstitialAtLevelTenShowsWithOneWin() {
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(level = 10, wins = 1, lastLevel = 0), cfg))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(level = 10, wins = 0, lastLevel = 0), cfg))
    }

    @Test
    fun firstEverExemptionNeverAppliesToAdFreeLevels() {
        for (l in 1..9) {
            assertEquals("L$l", skip(AdSkipReason.AD_FREE_LEVEL), InterstitialPolicy.decide(ok(level = l, wins = 1, lastLevel = 0), cfg))
        }
    }

    @Test
    fun firstEverExemptionKeepsOtherGates() {
        assertEquals(skip(AdSkipReason.SESSION_TOO_YOUNG), InterstitialPolicy.decide(ok(level = 10, wins = 1, now = 60_000L), cfg))
        assertEquals(skip(AdSkipReason.NO_CONSENT), InterstitialPolicy.decide(ok(level = 10, wins = 1, canRequestAds = false), cfg))
        assertEquals(skip(AdSkipReason.DAILY), InterstitialPolicy.decide(ok(level = 10, wins = 1, isDaily = true), cfg))
        assertEquals(skip(AdSkipReason.SESSION_CAP), InterstitialPolicy.decide(ok(level = 10, wins = 1, shown = cfg.maxPerSession), cfg))
    }

    @Test
    fun afterFirstAdNthWinRuleAppliesAgain() {
        assertEquals(skip(AdSkipReason.NOT_NTH_WIN), InterstitialPolicy.decide(ok(level = 11, wins = 1, lastLevel = 10), cfg))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(level = 12, wins = 2, lastLevel = 10), cfg))
    }

    @Test
    fun nonPositiveEveryNthIsTreatedAsEveryWin() {
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(wins = 1), cfg.copy(everyNthWin = 0)))
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
        assertEquals(skip(AdSkipReason.LEVEL_ALREADY_HAD_AD), InterstitialPolicy.decide(ok(level = 16, lastLevel = 16), cfg))
        assertEquals(skip(AdSkipReason.LEVEL_ALREADY_HAD_AD), InterstitialPolicy.decide(ok(level = 16, lastLevel = 19), cfg))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(level = 17, lastLevel = 16), cfg))
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
    fun loseRetryIsOffByDefault() {
        assertEquals(skip(AdSkipReason.NOT_A_BOUNDARY), InterstitialPolicy.decide(ok(trigger = AdTrigger.LOSE_RETRY), cfg))
    }

    @Test
    fun loseRetryWhenEnabledKeepsConsentLevelIntervalAndCapButIgnoresWinCount() {
        val on = cfg.copy(showOnLoseRetry = true)
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(ok(trigger = AdTrigger.LOSE_RETRY, wins = 0), on))
        assertEquals(skip(AdSkipReason.NO_CONSENT), InterstitialPolicy.decide(ok(trigger = AdTrigger.LOSE_RETRY, canRequestAds = false), on))
        assertEquals(skip(AdSkipReason.AD_FREE_LEVEL), InterstitialPolicy.decide(ok(trigger = AdTrigger.LOSE_RETRY, level = 9), on))
        assertEquals(
            skip(AdSkipReason.TOO_SOON_AFTER_LAST_AD),
            InterstitialPolicy.decide(ok(trigger = AdTrigger.LOSE_RETRY, lastAd = 1_000_000L - 10L), on),
        )
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
            for (level in listOf(1, 3, 9, 10, 40)) for (wins in listOf(0, 1, 2)) for (age in listOf(60_000L, 200_000L)) {
                val d = InterstitialPolicy.decide(
                    ok(canRequestAds = consent, inTutorial = tutorial, isDaily = daily, level = level, wins = wins, now = age, lastLevel = 1), cfg,
                )
                val expected = consent && !tutorial && !daily && level >= 10 && wins >= 2 && age >= 120_000L
                assertEquals("c=$consent t=$tutorial d=$daily l=$level w=$wins a=$age", expected, d == AdDecision.Show)
                if (d == AdDecision.Show) shows++
            }
        }
        assertTrue(shows > 0)
    }
}
