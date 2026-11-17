package com.tm.meow.sudoku.puzzle.domain.startup

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

    /** Max splash wait for the first Remote Config fetch + activate (B). Under the SDK's own 3 s network timeout. */
    const val RC_AWAIT_MS = 2_500L

    /** Warm-up is optional: stop waiting for it after this. */
    const val WARM_CAP_MS = 3_000L

    /**
     * Splash app-open ad load wait. Runs AFTER the splash loop above, so it is NOT counted against [MAX_MS].
     * Splash-specific: independent of AdPolicyConfig.loadWaitTimeoutMs (interstitial load wait).
     */
    const val APP_OPEN_WAIT_MS = 5_000L

    /**
     * First-launch app-open wait ceiling (15 s). Counted from splash start (loop ticks, UMP form time excluded), not
     * stacked after the loop: see [appOpenWaitMs]. Returning launches get the same ceiling (see [appOpenWaitMs]); [APP_OPEN_WAIT_MS] is only the floor.
     */
    const val APP_OPEN_WAIT_FIRST_MS = 15_000L

    /**
     * Wait budget handed to the splash app-open load wait, given the splash-loop [elapsedMs] already spent.
     * First and returning launches alike: whatever remains of [APP_OPEN_WAIT_FIRST_MS] (15 s from splash start), never below
     * [APP_OPEN_WAIT_MS]. The wait itself exits at once on ad ready / load finished without an ad.
     */
    @Suppress("UNUSED_PARAMETER")
    fun appOpenWaitMs(returning: Boolean, elapsedMs: Long): Long =
        (APP_OPEN_WAIT_FIRST_MS - elapsedMs).coerceAtLeast(APP_OPEN_WAIT_MS)

    /** Loop step for the ticker. */
    const val TICK_MS = 50L

    fun minMs(returning: Boolean): Long = if (returning) MIN_RETURNING_MS else MIN_FIRST_MS

    fun shouldProceed(elapsedMs: Long, consentDone: Boolean, warmDone: Boolean, returning: Boolean): Boolean =
        elapsedMs >= MAX_MS ||
            (consentDone && elapsedMs >= minMs(returning) && (warmDone || elapsedMs >= WARM_CAP_MS))
}
