package com.tm.meow.sudoku.puzzle.domain.ads

import com.tm.meow.sudoku.puzzle.domain.billing.AdsRemovedRepository
import com.tm.meow.sudoku.puzzle.domain.billing.NoAdsRemoved
import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.AdPlacement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-scoped holder (one per process) for ONE preloaded native ad per game placement, so the game screen binds instantly.
 * Pure Kotlin; the SDK is behind [NativeAdHandle]. Policy gates (consent, Remove Ads, switches, ad-free level, offline) live in the
 * use cases that call [preload]; this class only owns lifetime:
 *  - an entry older than [maxAgeMs] is destroyed on [take] / [preload] (native ads expire after ~1 h);
 *  - [take] hands the ad over (caller owns + destroys it) and removes it: an ad is never bound twice;
 *  - preloads run one at a time ([loadMutex]) so the shared native unit never has two cache loads in flight;
 *  - a failed preload is not retried within [retryAfterFailMs];
 *  - Remove Ads (or [clear]) destroys every held ad and drops in-flight results.
 */
class GameNativeAdCache(
    private val time: TimeProvider,
    private val scope: CoroutineScope,
    adsRemoved: AdsRemovedRepository = NoAdsRemoved,
    private val maxAgeMs: Long = MAX_AD_AGE_MS,
    private val retryAfterFailMs: Long = RETRY_AFTER_FAIL_MS,
) {
    private class Entry(val handle: NativeAdHandle, val loadedAtMs: Long)

    private val lock = Any()
    private val entries = HashMap<AdPlacement, Entry>()
    private val inFlight = HashMap<AdPlacement, Job>()
    private val failedAtMs = HashMap<AdPlacement, Long>()
    private val loadMutex = Mutex()
    private var generation = 0

    // Declared after the fields it touches (the collector may run inline on an immediate dispatcher).
    init {
        scope.launch { adsRemoved.adsRemoved.collect { removed -> if (removed) clear() } }
    }

    private fun isExpired(entry: Entry): Boolean = time.monotonicMillis() - entry.loadedAtMs >= maxAgeMs

    /** Fresh ad for [placement] or null. Removes it from the cache; an expired one is destroyed. */
    fun take(placement: AdPlacement): NativeAdHandle? = synchronized(lock) {
        val entry = entries.remove(placement) ?: return@synchronized null
        if (isExpired(entry)) {
            entry.handle.destroy()
            null
        } else {
            entry.handle
        }
    }

    /** Suspends until the in-flight preload for [placement] (if any) finishes. Returns at once when none. */
    suspend fun awaitPreload(placement: AdPlacement) {
        val job = synchronized(lock) { inFlight[placement] }
        job?.join()
    }

    /**
     * Start loading an ad for [placement] via [load] unless: a fresh one is held, one is already loading, or a preload failed
     * within the throttle window. [load] must apply its own gates and return null when gated / no fill. Never throws.
     */
    fun preload(placement: AdPlacement, load: suspend () -> NativeAdHandle?) {
        val job = synchronized(lock) {
            val held = entries[placement]
            if (held != null) {
                if (!isExpired(held)) return@synchronized null
                entries.remove(placement)
                held.handle.destroy()
            }
            if (inFlight[placement]?.isActive == true) return@synchronized null
            val failedAt = failedAtMs[placement]
            if (failedAt != null && time.monotonicMillis() - failedAt < retryAfterFailMs) return@synchronized null
            val gen = generation
            scope.launch(start = CoroutineStart.LAZY) { run(placement, gen, load) }.also { inFlight[placement] = it }
        }
        job?.start() // outside the lock: an immediate dispatcher runs the body inline
    }

    private suspend fun run(placement: AdPlacement, gen: Int, load: suspend () -> NativeAdHandle?) {
        val self = currentCoroutineContext()[Job]
        try {
            val handle = try {
                loadMutex.withLock { load() }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                null
            }
            store(placement, gen, handle)
        } finally {
            synchronized(lock) { if (inFlight[placement] === self) inFlight.remove(placement) }
        }
    }

    private fun store(placement: AdPlacement, gen: Int, handle: NativeAdHandle?) {
        var stale: NativeAdHandle? = null
        synchronized(lock) {
            if (handle == null) {
                failedAtMs[placement] = time.monotonicMillis()
            } else if (gen != generation) {
                stale = handle // cleared (Remove Ads) while loading
            } else {
                failedAtMs.remove(placement)
                stale = entries.put(placement, Entry(handle, time.monotonicMillis()))?.handle
            }
        }
        stale?.destroy()
    }

    /** Destroy every held ad, cancel in-flight preloads and forget failures. Safe to call repeatedly. */
    fun clear() {
        val (held, loading) = synchronized(lock) {
            generation++
            val h = entries.values.toList()
            val l = inFlight.values.toList()
            entries.clear()
            inFlight.clear()
            failedAtMs.clear()
            h to l
        }
        loading.forEach { it.cancel() }
        held.forEach { it.handle.destroy() }
    }

    companion object {
        /** Native ads expire after 1 hour; drop a little earlier. */
        const val MAX_AD_AGE_MS = 55 * 60 * 1000L
        const val RETRY_AFTER_FAIL_MS = 60_000L
    }
}
