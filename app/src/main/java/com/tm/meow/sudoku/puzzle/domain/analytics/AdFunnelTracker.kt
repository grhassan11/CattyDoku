package com.tm.meow.sudoku.puzzle.domain.analytics

import com.tm.meow.sudoku.puzzle.domain.crash.CrashReporter
import com.tm.meow.sudoku.puzzle.domain.crash.NoopCrashReporter

/** Values of the `ad_format` param. */
object AdFormat {
    const val APP_OPEN = "app_open"
    const val INTERSTITIAL = "interstitial"
    const val NATIVE = "native"
    const val REWARDED = "rewarded"
}

/** Event names of the ad funnel (one per step). */
object AdFunnelStep {
    const val REQUESTED = "ad_requested"
    const val LOADED = "ad_loaded"
    const val FAILED = "ad_failed"
    const val SHOWN = "ad_shown"
    const val DISMISSED = "ad_dismissed"
    const val CLICKED = "ad_clicked"
}

/**
 * Pure helper the ad repositories use: logs one funnel event and mirrors the last ad format / state into Crashlytics
 * keys (`last_ad_format`, `last_ad_state`). Never throws. Consent gating lives in the logger.
 */
class AdFunnelTracker(
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
    private val crash: CrashReporter = NoopCrashReporter,
) {
    fun requested(format: String) = emit(AdFunnelStep.REQUESTED, format)
    fun loaded(format: String, latencyMs: Long?) = emit(AdFunnelStep.LOADED, format, latencyMs = latencyMs)
    fun failed(format: String, errorCode: String?, latencyMs: Long?) {
        emit(AdFunnelStep.FAILED, format, errorCode, latencyMs)
        if (errorCode != null) crash.log("ad $format failed code=$errorCode")
    }
    fun shown(format: String, unitId: String = "") {
        emit(AdFunnelStep.SHOWN, format)
        // Native is inline: no dismissed() ever clears the keys, so it must not mark a full-screen ad as showing.
        if (format == AdFormat.NATIVE) return
        setShowingKeys(format, unitId.ifEmpty { "unknown" }, System.currentTimeMillis().toString())
    }
    fun dismissed(format: String) {
        emit(AdFunnelStep.DISMISSED, format)
        setShowingKeys("none", "none", "0")
    }

    /** Full-screen ad failed to show: clear the on-screen keys (no funnel event, load failures use [failed]). */
    fun showFailed() = setShowingKeys("none", "none", "0")

    /** ANR visibility: which full-screen ad is on screen and since when (epoch ms). Cleared to none/none/0. */
    private fun setShowingKeys(format: String, unit: String, sinceMs: String) {
        try {
            crash.setCustomKey("ad_showing_format", format)
            crash.setCustomKey("ad_showing_unit", unit)
            crash.setCustomKey("ad_showing_since", sinceMs)
        } catch (t: Throwable) {
            // telemetry must never affect ads
        }
    }
    fun clicked(format: String) = emit(AdFunnelStep.CLICKED, format)

    private fun emit(step: String, format: String, errorCode: String? = null, latencyMs: Long? = null) {
        try {
            analytics.log(AnalyticsEvent.AdFunnel(step, format, errorCode, latencyMs?.coerceAtLeast(0)))
            crash.setCustomKey("last_ad_format", format)
            crash.setCustomKey("last_ad_state", step.removePrefix("ad_"))
        } catch (t: Throwable) {
            // telemetry must never affect ads
        }
    }
}
