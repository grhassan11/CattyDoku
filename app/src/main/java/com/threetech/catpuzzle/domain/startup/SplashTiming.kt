package com.threetech.catpuzzle.domain.startup

/**
 * When the in-app splash may hand over. Pure: callers pass elapsed time and what finished.
 *
 * Two jobs run while the splash shows:
 *  - consent gate (UMP + MobileAds.initialize): REQUIRED, but never trusted to finish ([MAX_MS] hard cap);
 *  - warm-up (puzzle cache prefetch): best effort, abandoned after [WARM_CAP_MS].
 * No fake waits beyond the brand minimum. The caller does not advance [elapsedMs] while the UMP form is on
 * screen (the user is reading it), so the cap only measures our own waiting.
 */
object SplashTiming {
    /** First launch (terms not yet accepted): show the brand frame long enough to read. */
    const val MIN_FIRST_MS = 1_200L

    /** Returning launch: only a short beat so the cream frame does not read as a flash. */
    const val MIN_RETURNING_MS = 350L

    /** Hard cap: proceed even if consent / ads init is still running (ads stay off). */
    const val MAX_MS = 10_000L

    /** Warm-up is optional: stop waiting for it after this. */
    const val WARM_CAP_MS = 3_000L

    /** Loop step for the ticker. */
    const val TICK_MS = 50L

    fun minMs(returning: Boolean): Long = if (returning) MIN_RETURNING_MS else MIN_FIRST_MS

    fun shouldProceed(elapsedMs: Long, consentDone: Boolean, warmDone: Boolean, returning: Boolean): Boolean =
        elapsedMs >= MAX_MS ||
            (consentDone && elapsedMs >= minMs(returning) && (warmDone || elapsedMs >= WARM_CAP_MS))
}
