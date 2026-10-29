package com.threetech.catpuzzle.domain.ads

enum class AdShowResult {
    /** Ad was shown and dismissed by the user. */
    SHOWN,

    /** Nothing to show (not initialised, no ad loaded, wait timed out, no foreground activity). */
    NOT_READY,

    /** SDK reported a show failure. */
    FAILED,
}

/** Ads SDK boundary. Domain stays free of Android / SDK types; impl lives in data/ads. */
interface AdsRepository {
    /** Idempotent, once per process, off the main thread. Call ONLY when consent allows ads. */
    suspend fun initialize()

    /** Shows a preloaded interstitial if available (waits up to [loadWaitTimeoutMs] when one is still loading). Never throws. */
    suspend fun showIfReady(loadWaitTimeoutMs: Long): AdShowResult
}
