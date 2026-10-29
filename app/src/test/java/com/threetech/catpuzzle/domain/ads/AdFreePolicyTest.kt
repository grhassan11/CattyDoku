package com.threetech.catpuzzle.domain.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdFreePolicyTest {

    @Test
    fun constantsAreNineAndTen() {
        assertEquals(9, AdFreePolicy.AD_FREE_THROUGH_LEVEL)
        assertEquals(10, AdFreePolicy.FIRST_AD_LEVEL)
        assertEquals(9, AdPolicyConfig().adFreeThroughLevel)
        assertEquals(9, AppOpenConfig().adFreeThroughLevel)
    }

    @Test
    fun levelsOneThroughNineAreAdFreeTenOnwardAllowed() {
        for (l in 1..9) {
            assertTrue("L$l", AdFreePolicy.isAdFree(l))
            assertFalse("L$l", AdFreePolicy.adsAllowed(l))
        }
        for (l in listOf(10, 11, 50, 1_000)) {
            assertFalse("L$l", AdFreePolicy.isAdFree(l))
            assertTrue("L$l", AdFreePolicy.adsAllowed(l))
        }
    }

    @Test
    fun noProgressIsAdFree() {
        assertTrue(AdFreePolicy.isAdFree(0))
        assertTrue(AdFreePolicy.isAdFree(-3))
    }

    @Test
    fun thresholdIsConfigurable() {
        assertTrue(AdFreePolicy.adsAllowed(4, throughLevel = 3))
        assertTrue(AdFreePolicy.isAdFree(3, throughLevel = 3))
        assertTrue(AdFreePolicy.adsAllowed(1, throughLevel = 0))
    }

    // ---- app-open uses the same rule ----

    private fun appOpenCtx(level: Int) = AppOpenContext(
        coldStart = false,
        canRequestAds = true,
        sdkInitialized = true,
        startFlowComplete = true,
        fullScreenAdShowing = false,
        consentFormShowing = false,
        returnedFromAd = false,
        nowMs = 1_000_000L,
        backgroundedAtMs = 0L,
        lastAdShownMs = null,
        appOpenShownThisSession = 0,
        adAgeMs = 1_000L,
        playerLevel = level,
    )

    @Test
    fun appOpenSkipsThroughLevelNineAndShowsFromTen() {
        for (l in listOf(0, 1, 5, 9)) {
            assertEquals(
                "L$l",
                AppOpenDecision.Skip(AppOpenSkipReason.AD_FREE_LEVEL),
                AppOpenPolicy.decide(appOpenCtx(l)),
            )
        }
        assertEquals(AppOpenDecision.Show, AppOpenPolicy.decide(appOpenCtx(10)))
    }

    @Test
    fun appOpenContextDefaultsToPastTheGate() {
        val ctx = appOpenCtx(11).copy(playerLevel = Int.MAX_VALUE)
        assertEquals(AppOpenDecision.Show, AppOpenPolicy.decide(ctx))
    }
}
