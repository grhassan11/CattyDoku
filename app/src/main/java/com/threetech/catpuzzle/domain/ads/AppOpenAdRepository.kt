package com.threetech.catpuzzle.domain.ads

/** App-open ad SDK boundary. Impl lives in data/ads. Single-use ads: a shown ad is never reused. */
interface AppOpenAdRepository {
    /** Idempotent: no-op when not initialised, already loading, or a loaded ad is held. Never throws. */
    fun preload()

    /** Age of the held ad in monotonic ms, or null when none is held. */
    fun readyAdAgeMs(): Long?

    /** Drop the held ad (expired). */
    fun discard()

    /**
     * Show the held ad on the foreground activity; suspends until dismissed / failed. The ad is consumed.
     * Returns [AdShowResult.NOT_READY] when none is held. Never throws. Does NOT preload the next one.
     */
    suspend fun show(): AdShowResult
}
