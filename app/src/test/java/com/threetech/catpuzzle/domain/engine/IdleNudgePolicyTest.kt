package com.threetech.catpuzzle.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleNudgePolicyTest {
    @Test
    fun quietBeforeIdleDelay() {
        assertFalse(IdleNudgePolicy.isNudging(0))
        assertFalse(IdleNudgePolicy.isNudging(-5))
        assertFalse(IdleNudgePolicy.isNudging(IdleNudgePolicy.IDLE_DELAY_MS - 1))
    }

    @Test
    fun nudgesAtIdleDelayForDuration() {
        val d = IdleNudgePolicy.IDLE_DELAY_MS
        assertTrue(IdleNudgePolicy.isNudging(d))
        assertTrue(IdleNudgePolicy.isNudging(d + IdleNudgePolicy.NUDGE_DURATION_MS - 1))
        assertFalse(IdleNudgePolicy.isNudging(d + IdleNudgePolicy.NUDGE_DURATION_MS))
    }

    @Test
    fun repeatsAfterAnotherIdlePeriod() {
        val cycle = IdleNudgePolicy.IDLE_DELAY_MS + IdleNudgePolicy.NUDGE_DURATION_MS
        assertFalse(IdleNudgePolicy.isNudging(cycle + IdleNudgePolicy.IDLE_DELAY_MS - 1))
        assertTrue(IdleNudgePolicy.isNudging(cycle + IdleNudgePolicy.IDLE_DELAY_MS))
    }

    @Test
    fun boosterNeedsStock() {
        assertFalse(IdleNudgePolicy.boosterEligible(0))
        assertTrue(IdleNudgePolicy.boosterEligible(1))
    }

    @Test
    fun screenEligibility() {
        assertTrue(IdleNudgePolicy.screenEligible(playable = true, overlayOpen = false, resumed = true))
        assertFalse(IdleNudgePolicy.screenEligible(playable = false, overlayOpen = false, resumed = true))
        assertFalse(IdleNudgePolicy.screenEligible(playable = true, overlayOpen = true, resumed = true))
        assertFalse(IdleNudgePolicy.screenEligible(playable = true, overlayOpen = false, resumed = false))
    }
}
