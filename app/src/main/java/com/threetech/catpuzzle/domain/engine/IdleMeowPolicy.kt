package com.threetech.catpuzzle.domain.engine

import kotlin.random.Random

/**
 * Idle meow scheduling. Starts when the idle nudge starts ([IdleNudgePolicy.IDLE_DELAY_MS] after the last input).
 * Each round waits a random [nextDelayMs] ([MIN_DELAY_MS]..[MAX_DELAY_MS]), then meows with [PROBABILITY], and only if
 * [MIN_GAP_MS] passed since the previous meow. All timing constants live here. Pure; the UI drives it.
 */
object IdleMeowPolicy {
    const val MIN_DELAY_MS = 12_000L
    const val MAX_DELAY_MS = 25_000L
    const val MIN_GAP_MS = 12_000L
    const val PROBABILITY = 0.6

    /** Random wait before the next meow roll, inclusive range. */
    fun nextDelayMs(random: Random): Long = random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1)

    /** Probability roll. */
    fun roll(random: Random): Boolean = random.nextDouble() < PROBABILITY

    /** Min gap since the last meow. [lastMeowAtMs] null = never meowed. */
    fun gapElapsed(nowMs: Long, lastMeowAtMs: Long?): Boolean =
        lastMeowAtMs == null || nowMs - lastMeowAtMs >= MIN_GAP_MS

    /** Roll + gap in one call. */
    fun shouldMeow(random: Random, nowMs: Long, lastMeowAtMs: Long?): Boolean =
        gapElapsed(nowMs, lastMeowAtMs) && roll(random)
}
