package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.ads.AdLogger
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.AdsRepository
import com.tm.meow.sudoku.puzzle.domain.ads.NoopAdLogger
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.network.AlwaysOnline
import com.tm.meow.sudoku.puzzle.domain.network.ConnectivityObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex

/**
 * Late-online recovery: the app started (or lost the network) offline, so UMP consent / MobileAds init / Remote Config /
 * ad preloads were skipped or failed. When the network is back this redoes, in the canonical order and single-flight:
 *  1. UMP consent gather, only if the earlier one never completed an info update ([AdSessionTracker.consentGatherPending]);
 *  2. ads SDK init, only if consent allows and the SDK is not up (repo init is mutex-guarded + idempotent; adapters init inside it);
 *  3. Remote Config fetch + activate (consent-gated by [refreshRemoteConfig]; the repo makes it idempotent). A late activation replaces
 *     the repository snapshot, so live consumers (AdPolicySource) apply it to the next decision (see RemoteConfigRepository);
 *  4. [preloadAds] (interstitial + app-open; each use case keeps its own consent / Remove Ads / offline / kill-switch gates).
 * Native slots and the rewarded surface are state-driven (they collect connectivity / consent / SDK init) and need no kick here.
 * Does nothing before the startup gather finished (the splash owns that), while the UMP form is up, or when offline.
 * Never throws. Call on the main thread (the interstitial preloader is main-thread only).
 */
class RecoverAfterReconnect(
    private val consent: ConsentRepository,
    private val ads: AdsRepository,
    private val tracker: AdSessionTracker,
    private val refreshRemoteConfig: RefreshRemoteConfig,
    private val preloadAds: () -> Unit,
    private val connectivity: ConnectivityObserver = AlwaysOnline,
    private val logger: AdLogger = NoopAdLogger,
) {
    private val lock = Mutex()

    private val startupSettled: Boolean
        get() = tracker.startupGatherStarted && tracker.startupGatherFinished && !tracker.consentFormShowing

    /** Consent still to (re)gather, or consent granted but the SDK is not up. Cheap, side-effect free. */
    fun needsRecovery(): Boolean {
        if (!connectivity.isOnline.value || !startupSettled) return false
        return tracker.consentGatherPending || (consent.state.value.canRequestAds && !ads.isSdkInitialized)
    }

    /** One recovery pass. Returns true when nothing is left pending (consent gathered, SDK up when allowed). */
    suspend fun run(): Boolean {
        if (!connectivity.isOnline.value || !startupSettled) return false
        if (!lock.tryLock()) return false // single-flight: a pass is already running
        try {
            if (tracker.consentGatherPending) {
                logger.log("reconnect: re-gather consent")
                safely { consent.gatherConsent() }
            }
            if (consent.state.value.canRequestAds && !ads.isSdkInitialized) {
                logger.log("reconnect: init ads sdk")
                safely { ads.initialize() }
            }
            safely { refreshRemoteConfig() }
            if (consent.state.value.canRequestAds && ads.isSdkInitialized) {
                safely { preloadAds() }
            }
            return isComplete()
        } finally {
            lock.unlock()
        }
    }

    /**
     * [run] now, then bounded retries (one per entry of [retryDelaysMs]) while still incomplete and online. The caller cancels
     * this on going offline / on the next reconnect, so flapping never stacks passes. Never throws (except cancellation).
     */
    suspend fun runWithRetries(retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS) {
        if (run()) return
        for (wait in retryDelaysMs) {
            delay(wait)
            if (!connectivity.isOnline.value) return
            if (run()) return
        }
    }

    private fun isComplete(): Boolean =
        !tracker.consentGatherPending && (!consent.state.value.canRequestAds || ads.isSdkInitialized)

    companion object {
        val DEFAULT_RETRY_DELAYS_MS = listOf(15_000L, 30_000L, 60_000L)
    }
}

private inline fun safely(block: () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        // fail open
    }
}
