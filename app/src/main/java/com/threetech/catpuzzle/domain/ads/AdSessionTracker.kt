package com.threetech.catpuzzle.domain.ads

import com.threetech.catpuzzle.domain.time.TimeProvider
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
    @Volatile var sessionStartMs: Long = time.monotonicMillis()
        private set

    @Volatile var lastAdShownMs: Long? = null
        private set

    @Volatile var winsSinceLastAd: Int = 0
        private set

    @Volatile var adsShown: Int = 0
        private set

    /** App-open ads shown this process (separate from [adsShown], which caps interstitials). */
    @Volatile var appOpenShown: Int = 0
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
     * App-open shown: starts the shared full-screen cool-down (interstitial min interval reads [lastAdShownMs]) but does
     * NOT reset [winsSinceLastAd] or count toward the interstitial [adsShown] cap.
     */
    fun recordAppOpenShown() {
        lastAdShownMs = time.monotonicMillis()
        appOpenShown += 1
    }

    /**
     * Rewarded ad shown (user opted in): starts the shared full-screen cool-down ([lastAdShownMs], read by the interstitial
     * min interval AND the 180 s app-open rule) but does NOT reset [winsSinceLastAd] or count toward the interstitial cap.
     */
    fun recordRewardedShown() {
        lastAdShownMs = time.monotonicMillis()
    }

    fun recordWin() {
        winsSinceLastAd += 1
    }

    fun recordShown() {
        lastAdShownMs = time.monotonicMillis()
        winsSinceLastAd = 0
        adsShown += 1
    }

    fun now(): Long = time.monotonicMillis()

    private companion object {
        const val NONE = -1L
        const val CLICK_WINDOW_MS = 10_000L
    }
}
