package com.tm.meow.sudoku.puzzle.domain.ads

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

    /** True once the SDK is up. Default true: no-op / fake impls have nothing to retry. Real impl overrides. */
    val isSdkInitialized: Boolean get() = true

    /**
     * Idempotent readiness kick for the interstitial: starts loading when idle, never double-loads while loading or cached,
     * refreshes an expired cached ad, retries after a failure with bounded backoff. Default no-op (fakes). Never throws.
     */
    fun preloadInterstitial() = Unit

    /** Shows a preloaded interstitial if available (waits up to [loadWaitTimeoutMs] when one is still loading). Never throws. */
    suspend fun showIfReady(loadWaitTimeoutMs: Long): AdShowResult
}
