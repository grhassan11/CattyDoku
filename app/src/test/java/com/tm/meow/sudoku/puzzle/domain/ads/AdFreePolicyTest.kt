package com.tm.meow.sudoku.puzzle.domain.ads

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

    // ---- app-open has no level gate ----

    private fun appOpenCtx() = AppOpenContext(
        coldStart = false,
        canRequestAds = true,
        sdkInitialized = true,
        fullScreenAdShowing = false,
        consentFormShowing = false,
        adAgeMs = 1_000L,
    )

    @Test
    fun appOpenHasNoLevelGate() {
        assertEquals(AppOpenDecision.Show, AppOpenPolicy.decide(appOpenCtx()))
    }
}
