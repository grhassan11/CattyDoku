package com.threetech.catpuzzle.domain.engine

import kotlin.random.Random
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleMeowPolicyTest {
    @Test
    fun delayStaysInRange() {
        val r = Random(7)
        repeat(500) {
            val d = IdleMeowPolicy.nextDelayMs(r)
            assertTrue(d in IdleMeowPolicy.MIN_DELAY_MS..IdleMeowPolicy.MAX_DELAY_MS)
        }
    }

    @Test
    fun gapBlocksTooSoon() {
        assertTrue(IdleMeowPolicy.gapElapsed(1_000L, null))
        assertFalse(IdleMeowPolicy.gapElapsed(5_000L, 0L))
        assertTrue(IdleMeowPolicy.gapElapsed(IdleMeowPolicy.MIN_GAP_MS, 0L))
    }

    @Test
    fun shouldMeowRespectsGapEvenIfRollWins() {
        val r = Random(1)
        repeat(200) { assertFalse(IdleMeowPolicy.shouldMeow(r, 1_000L, 0L)) }
    }

    @Test
    fun rollRateNearProbability() {
        val r = Random(42)
        val n = 10_000
        val hits = (1..n).count { IdleMeowPolicy.roll(r) }
        val rate = hits.toDouble() / n
        assertTrue(rate in 0.55..0.65)
    }
}
