package com.threetech.catpuzzle.domain.engine

/**
 * Idle nudge timing: after [IDLE_DELAY_MS] without input the Cat / Hint booster icons wiggle for
 * [NUDGE_DURATION_MS], rest, then repeat (cycle = idle delay + nudge duration) until the next input.
 * Pure; the UI drives it with delays and only reads these constants / predicates.
 */
object IdleNudgePolicy {
    const val IDLE_DELAY_MS = 8_000L
    const val NUDGE_DURATION_MS = 4_000L

    /** A booster is worth nudging only with stock; an ad refill alone is not enough. */
    fun boosterEligible(charges: Int): Boolean = charges > 0

    /** The nudge only runs while the game is playable and nothing covers it. */
    fun screenEligible(playable: Boolean, overlayOpen: Boolean, resumed: Boolean): Boolean =
        playable && !overlayOpen && resumed

    /** Is the nudge animating [idleElapsedMs] after the last input? Negative elapsed counts as 0. */
    fun isNudging(idleElapsedMs: Long): Boolean {
        if (idleElapsedMs < IDLE_DELAY_MS) return false
        val cycle = IDLE_DELAY_MS + NUDGE_DURATION_MS
        return (idleElapsedMs - IDLE_DELAY_MS) % cycle < NUDGE_DURATION_MS
    }
}
