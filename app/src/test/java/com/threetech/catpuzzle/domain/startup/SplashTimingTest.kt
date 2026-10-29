package com.threetech.catpuzzle.domain.startup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashTimingTest {

    private fun proceed(elapsed: Long, consent: Boolean, warm: Boolean, returning: Boolean) =
        SplashTiming.shouldProceed(elapsed, consent, warm, returning)

    @Test
    fun firstLaunchWaitsForMinimumEvenWhenEverythingIsDone() {
        assertFalse(proceed(0, consent = true, warm = true, returning = false))
        assertFalse(proceed(SplashTiming.MIN_FIRST_MS - 1, true, true, false))
        assertTrue(proceed(SplashTiming.MIN_FIRST_MS, true, true, false))
    }

    @Test
    fun returningLaunchProceedsAfterShortBeat() {
        assertFalse(proceed(SplashTiming.MIN_RETURNING_MS - 1, true, true, true))
        assertTrue(proceed(SplashTiming.MIN_RETURNING_MS, true, true, true))
        assertTrue(SplashTiming.MIN_RETURNING_MS < SplashTiming.MIN_FIRST_MS)
    }

    @Test
    fun neverProceedsWithoutConsentBeforeTheHardCap() {
        assertFalse(proceed(SplashTiming.MIN_FIRST_MS, consent = false, warm = true, returning = false))
        assertFalse(proceed(SplashTiming.MAX_MS - 1, consent = false, warm = true, returning = true))
    }

    @Test
    fun hardCapForcesProceedEvenIfConsentAndWarmUpHang() {
        assertFalse(proceed(SplashTiming.MAX_MS - 1, consent = false, warm = false, returning = false))
        assertTrue(proceed(SplashTiming.MAX_MS, consent = false, warm = false, returning = false))
        assertTrue(proceed(SplashTiming.MAX_MS, consent = false, warm = false, returning = true))
    }

    @Test
    fun warmUpIsOptionalAfterItsOwnCap() {
        assertFalse(proceed(SplashTiming.WARM_CAP_MS - 1, consent = true, warm = false, returning = true))
        assertTrue(proceed(SplashTiming.WARM_CAP_MS, consent = true, warm = false, returning = true))
    }

    @Test
    fun capsAreOrdered() {
        assertTrue(SplashTiming.MIN_FIRST_MS < SplashTiming.WARM_CAP_MS)
        assertTrue(SplashTiming.WARM_CAP_MS < SplashTiming.MAX_MS)
    }
}
