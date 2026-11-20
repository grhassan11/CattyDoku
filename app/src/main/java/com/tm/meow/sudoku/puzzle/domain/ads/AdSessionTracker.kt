package com.tm.meow.sudoku.puzzle.domain.ads

import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory, per-PROCESS ad bookkeeping (resets on every cold start by design: "session" = process).
 * Not persisted: the only persisted ad flags are lastInterstitialLevel and sessionAdCount (see AppFlags).
 * Created when first injected, which is the consent step at startup, i.e. process start.
 * Main-thread use; fields are @Volatile for safety.
 */
class AdSessionTracker(private val time: TimeProvider) {
    @Volatile var lastAdShownMs: Long? = null
        private set

    @Volatile var winsSinceLastAd: Int = 0
        private set

    /** Sudoku wins since the last shown interstitial (own cadence; Levels keeps [winsSinceLastAd]). */
    @Volatile var sudokuWinsSinceLastAd: Int = 0
        private set

    private val fullScreenAdShowingFlow = MutableStateFlow(false)

    /** Observable mirror of [fullScreenAdShowing]; the audio controller silences music + SFX while true. */
    val fullScreenAdShowingState: StateFlow<Boolean> = fullScreenAdShowingFlow.asStateFlow()

    /** An interstitial, app-open or rewarded ad is on screen right now. Blocks app-open. Thread-safe (StateFlow-backed). */
    var fullScreenAdShowing: Boolean
        get() = fullScreenAdShowingFlow.value
        set(value) {
            fullScreenAdShowingFlow.value = value
        }

    /** UMP form is on screen right now. Blocks app-open. */
    @Volatile var consentFormShowing: Boolean = false

    /** Current screen is one where app-open must not show (Settings + its sub-screens). Set by navigation, read on each foreground. */
    @Volatile var appOpenSuppressedScreen: Boolean = false

    @Volatile private var externalLaunchPending: Boolean = false

    /**
     * App sent the user out on purpose (feedback / rate-us email, privacy policy link, store listing). The matching
     * foreground return is an app-open moment even on a suppressed screen (Settings). One-shot, see [consumeExternalLaunch].
     */
    fun markExternalLaunch() {
        externalLaunchPending = true
    }

    /** True once per [markExternalLaunch] (clears it). Consumed on every foreground so the flag cannot stick. */
    fun consumeExternalLaunch(): Boolean {
        val was = externalLaunchPending
        externalLaunchPending = false
        return was
    }

    /** The consent gather + ads init startup step has begun this process (in-app splash, or the restored-back-stack fallback). */
    @Volatile var startupGatherStarted: Boolean = false

    /** The consent gather + first ads-init attempt have finished this process (success or not). Gate for init retries. */
    @Volatile var startupGatherFinished: Boolean = false

    /**
     * The last UMP consent gather did not complete an info update (offline, no foreground Activity, timeout, error). Set by the
     * consent repository, cleared on a successful update. While true, a late-online recovery re-runs the gather.
     */
    @Volatile var consentGatherPending: Boolean = false

    /** Home reached this process = Splash / Terms / consent / notification prompt / Tutorial are all behind us. */
    @Volatile var startFlowComplete: Boolean = false

    @Volatile private var adClickedAtMs: Long = NONE

    /** User tapped an ad (may leave the app: browser / store). The matching foreground is not an app-open moment. */
    fun markAdClicked() {
        adClickedAtMs = time.monotonicMillis()
    }

    /** True once per click (clears it) and only if [windowMs] has not passed, so a stale click cannot suppress a later ad. */
    fun consumeAdClicked(windowMs: Long = CLICK_WINDOW_MS): Boolean {
        val at = adClickedAtMs
        adClickedAtMs = NONE
        return at != NONE && time.monotonicMillis() - at <= windowMs
    }

    /**
     * App-open shown: starts the shared full-screen cool-down ([lastAdShownMs], read by app-open and Rate us) but does
     * NOT reset [winsSinceLastAd].
     */
    fun recordAppOpenShown() {
        lastAdShownMs = time.monotonicMillis()
    }

    /**
     * Rewarded ad shown (user opted in): starts the shared full-screen cool-down ([lastAdShownMs], read by Rate us) but does NOT reset [winsSinceLastAd].
     */
    fun recordRewardedShown() {
        lastAdShownMs = time.monotonicMillis()
    }

    fun recordWin() {
        winsSinceLastAd += 1
    }

    fun recordSudokuWin() {
        sudokuWinsSinceLastAd += 1
    }

    fun recordShown() {
        lastAdShownMs = time.monotonicMillis()
        winsSinceLastAd = 0
        sudokuWinsSinceLastAd = 0
    }

    /** When a gameplay-BACK interstitial was last shown this process (null = none). Read by the win-after-back guard. */
    @Volatile var lastBackAdShownMs: Long? = null
        private set

    /** Call after [recordShown] when the shown ad was a gameplay-BACK interstitial. */
    fun recordBackAdShown() {
        lastBackAdShownMs = time.monotonicMillis()
    }

    fun now(): Long = time.monotonicMillis()

    private companion object {
        const val NONE = -1L
        /** Click-out (browser / store) can last minutes; a click older than this no longer suppresses an app-open. */
        const val CLICK_WINDOW_MS = 5 * 60_000L
    }
}
