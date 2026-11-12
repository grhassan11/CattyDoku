package com.tm.meow.sudoku.puzzle.data.ads

import android.content.Context
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader
import com.tm.meow.sudoku.puzzle.BuildConfig
import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicyConfig
import com.tm.meow.sudoku.puzzle.domain.network.ConnectivityObserver
import com.tm.meow.sudoku.puzzle.data.platform.CurrentActivityProvider
import com.tm.meow.sudoku.puzzle.di.IoDispatcher
import com.tm.meow.sudoku.puzzle.di.MainDispatcher
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.AdShowResult
import com.tm.meow.sudoku.puzzle.domain.ads.AdsRepository
import com.tm.meow.sudoku.puzzle.domain.ads.AdsSdkStatus
import com.tm.meow.sudoku.puzzle.domain.billing.AdsRemovedRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import com.tm.meow.sudoku.puzzle.domain.crash.CrashReporter
import com.tm.meow.sudoku.puzzle.domain.crash.NoopCrashReporter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * GMA Next-Gen SDK wrapper (com.google.android.libraries.ads.mobile.sdk). Interstitial (preload API) + SDK init state ([AdsSdkStatus]) shared with native / app-open.
 * IDs come from BuildConfig (debug = Google test IDs; release = placeholders until the user supplies real ones).
 * A release build with placeholder IDs never initialises the SDK (ads stay off, app unaffected).
 * Child-directed / under-age: app is general audience, RequestConfiguration left at SDK defaults (decision, not an omission).
 */
@Singleton
class AdsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activities: CurrentActivityProvider,
    private val tracker: AdSessionTracker,
    private val adsRemoved: AdsRemovedRepository,
    private val policy: AdPolicyConfig,
    @IoDispatcher private val io: CoroutineDispatcher,
    @MainDispatcher main: CoroutineDispatcher,
    private val connectivity: ConnectivityObserver,
    private val crash: CrashReporter = NoopCrashReporter,
) : AdsRepository, AdsSdkStatus {

    private val initMutex = Mutex()

    private val _initialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _initialized

    override val isSdkInitialized: Boolean get() = _initialized.value

    @Volatile private var lastPreloadFailed = false

    /** True while the interstitial preloader is running (main thread only). */
    private var preloading = false
    private var retryJob: Job? = null
    /** A retry was skipped because the device was offline (no attempt spent); the next online trigger restarts the preload. */
    private var parkedOffline = false
    private var failedAttempts = 0
    private var lastRequestMs = 0L

    /** Monotonic time the current cached ad became available (0 = none known). Used for the ~1h expiry guard. */
    private var bufferedSinceMs = 0L
    private val scope = CoroutineScope(SupervisorJob() + main)

    private val unitId: String get() = BuildConfig.ADMOB_INTERSTITIAL_ID

    private val idsConfigured: Boolean
        get() = !BuildConfig.ADMOB_APP_ID.contains(PLACEHOLDER_MARK) && !unitId.contains(PLACEHOLDER_MARK)

    override suspend fun initialize() {
        if (_initialized.value) return
        initMutex.withLock {
            if (_initialized.value) return
            if (!idsConfigured) {
                Log.w(TAG, "AdMob IDs are placeholders (release TBD): ads disabled")
                return
            }
            // Background thread per Google guidance.
            try {
                withContext(io) {
                    MobileAds.initialize(context, InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID).build()) {
                        // adapter initialisation complete (not awaited: bidding adapters must not delay the first ad)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // Stay uninitialised: a later initialize() call (resume / ensure) retries.
                dbg("MobileAds.initialize FAILED, will retry on next trigger: $t")
                crash.recordNonFatal(t, "ads.init")
                return
            }
            _initialized.value = true
            // Remove Ads: do not request interstitials at all. The collector stops the preloader when the entitlement
            // turns on at runtime (bought). Starting is driven by game-screen entry via [preloadInterstitial] (policy-gated).
            scope.launch {
                adsRemoved.adsRemoved.collect { removed ->
                    if (removed) stopPreload()
                }
            }
        }
    }

    private fun stopPreload() {
        retryJob?.cancel()
        retryJob = null
        failedAttempts = 0
        bufferedSinceMs = 0L
        lastPreloadFailed = false
        if (!preloading) return
        preloading = false
        try {
            InterstitialAdPreloader.destroy(unitId)
        } catch (t: Throwable) {
            Log.w(TAG, "interstitial preloader destroy failed", t)
        }
    }

    /**
     * Idempotent readiness kick (main thread). Never double-loads: no-op while a load is in flight or a fresh ad is cached.
     * Starts the preloader when idle, restarts it when the cached ad is older than [AD_MAX_AGE_MS] (interstitials expire
     * ~1h) and, after the bounded retries gave up, restarts it on this next trigger (throttled).
     */
    override fun preloadInterstitial() {
        try {
            if (!_initialized.value || !policy.anyInterstitialShow || adsRemoved.adsRemoved.value) return
            if (!preloading) {
                dbg("interstitial load requested (idle, start)")
                startPreload()
                return
            }
            if (InterstitialAdPreloader.isAdAvailable(unitId)) {
                if (isStale()) {
                    dbg("interstitial expired (older than ${AD_MAX_AGE_MS / 60_000} min), discard + reload")
                    restartPreload()
                }
                return
            }
            // Failed while offline (retry was parked, attempts not spent): connectivity is back, start fresh.
            if (parkedOffline && lastPreloadFailed && retryJob?.isActive != true && connectivity.isOnline.value) {
                dbg("interstitial load requested (parked offline failure, back online)")
                restartPreload()
                return
            }
            if (lastPreloadFailed && retryJob?.isActive != true && failedAttempts >= RETRY_DELAYS_MS.size &&
                tracker.now() - lastRequestMs >= TRIGGER_THROTTLE_MS
            ) {
                dbg("interstitial load requested (retries exhausted, new trigger)")
                restartPreload()
                return
            }
            dbg("interstitial load skipped: already loading / cached (failed=$lastPreloadFailed attempts=$failedAttempts)")
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            dbg("preloadInterstitial error: $t")
        }
    }

    private fun isStale(): Boolean = bufferedSinceMs != 0L && tracker.now() - bufferedSinceMs > AD_MAX_AGE_MS

    private fun restartPreload() {
        parkedOffline = false
        stopPreload()
        startPreload()
    }

    private fun startPreload() {
        if (preloading) return
        // All four per-surface interstitial switches off: never request an interstitial.
        if (!policy.anyInterstitialShow) return
        preloading = true
        lastPreloadFailed = false
        bufferedSinceMs = 0L
        lastRequestMs = tracker.now()
        val request = AdRequest.Builder(unitId).build()
        val config = PreloadConfiguration(request)
        try {
            InterstitialAdPreloader.start(
                unitId,
                config,
                object : PreloadCallback {
                    override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) {
                        scope.launch {
                            lastPreloadFailed = false
                            failedAttempts = 0
                            retryJob?.cancel()
                            retryJob = null
                            if (bufferedSinceMs == 0L) bufferedSinceMs = tracker.now()
                            dbg("interstitial loaded")
                        }
                    }

                    override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) {
                        lastPreloadFailed = true
                        dbg("interstitial load failed: $adError")
                        scope.launch { scheduleRetry() }
                    }

                    override fun onAdsExhausted(preloadId: String) = Unit
                },
            )
        } catch (t: Throwable) {
            preloading = false
            dbg("interstitial preloader start threw: $t")
        }
    }

    /** Bounded backoff (5 s, 15 s, 30 s). After the last attempt it stops; the next [preloadInterstitial] trigger restarts. */
    private fun scheduleRetry() {
        if (retryJob?.isActive == true || !preloading) return
        if (!connectivity.isOnline.value) {
            parkedOffline = true
            dbg("interstitial retry parked: offline (no attempt spent)")
            return
        }
        val attempt = failedAttempts
        if (attempt >= RETRY_DELAYS_MS.size) {
            dbg("interstitial load retries exhausted, waiting for next trigger")
            return
        }
        failedAttempts = attempt + 1
        val wait = RETRY_DELAYS_MS[attempt]
        retryJob = scope.launch {
            dbg("interstitial retry ${attempt + 1}/${RETRY_DELAYS_MS.size} in ${wait}ms")
            delay(wait)
            retryJob = null
            if (!connectivity.isOnline.value) {
                failedAttempts = attempt // refund: went offline during the backoff
                parkedOffline = true
                dbg("interstitial retry parked: offline (attempt refunded)")
                return@launch
            }
            if (adsRemoved.adsRemoved.value || !policy.anyInterstitialShow) return@launch
            if (InterstitialAdPreloader.isAdAvailable(unitId)) return@launch
            val used = failedAttempts
            restartPreload() // resets counters
            failedAttempts = used
        }
    }

    override suspend fun showIfReady(loadWaitTimeoutMs: Long): AdShowResult {
        if (!_initialized.value) {
            dbg("showIfReady NOT_READY: SDK not initialized (idsConfigured=$idsConfigured)")
            return AdShowResult.NOT_READY
        }
        return try {
            withContext(Dispatchers.Main.immediate) {
                // Start if idle (entry kick missed) and drop an expired cached ad before waiting.
                preloadInterstitial()
            }
            if (!awaitAvailable(loadWaitTimeoutMs)) {
                dbg("showIfReady NOT_READY: no buffered ad (lastPreloadFailed=$lastPreloadFailed, preloading=$preloading, waitMs=$loadWaitTimeoutMs)")
                return AdShowResult.NOT_READY
            }
            withContext(Dispatchers.Main.immediate) {
                val activity = activities.current() ?: run {
                    dbg("showIfReady NOT_READY: no current activity")
                    return@withContext AdShowResult.NOT_READY
                }
                // Do not store the ad: poll at the placement. The preloader refills its buffer after a poll,
                // which is the "preload next after dismissal" requirement.
                val ad = InterstitialAdPreloader.pollAd(unitId) ?: run {
                    dbg("showIfReady NOT_READY: pollAd returned null")
                    return@withContext AdShowResult.NOT_READY
                }
                suspendCancellableCoroutine { cont ->
                    ad.adEventCallback = object : InterstitialAdEventCallback {
                        override fun onAdShowedFullScreenContent() = Unit
                        override fun onAdImpression() = Unit
                        // Click-out (browser / store): the matching foreground must not trigger an app-open ad.
                        override fun onAdClicked() = tracker.markAdClicked()

                        override fun onAdDismissedFullScreenContent() {
                            bufferedSinceMs = 0L
                            if (cont.isActive) cont.resume(AdShowResult.SHOWN)
                            preloadInterstitial() // next one (the preloader also refills after pollAd; idempotent)
                        }

                        override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                            dbg("showIfReady FAILED: onAdFailedToShowFullScreenContent $fullScreenContentError")
                            bufferedSinceMs = 0L
                            preloadInterstitial()
                            if (cont.isActive) cont.resume(AdShowResult.FAILED)
                        }
                    }
                    ad.show(activity)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            dbg("showIfReady FAILED: $t")
            crash.recordNonFatal(t, "interstitial.show")
            AdShowResult.FAILED
        }
    }

    private fun dbg(message: String) {
        if (BuildConfig.DEBUG) Log.d(DEBUG_TAG, message)
    }

    /** Ready now, or (unless the last preload failed) wait up to [timeoutMs] for one in flight. */
    private suspend fun awaitAvailable(timeoutMs: Long): Boolean {
        if (InterstitialAdPreloader.isAdAvailable(unitId)) return true
        if (lastPreloadFailed || timeoutMs <= 0) return false
        return withTimeoutOrNull(timeoutMs) {
            while (!InterstitialAdPreloader.isAdAvailable(unitId)) {
                if (lastPreloadFailed) return@withTimeoutOrNull false
                delay(POLL_MS)
            }
            true
        } ?: false
    }

    private companion object {
        const val TAG = "AdsRepository"
        const val DEBUG_TAG = "MeowAds"
        const val POLL_MS = 100L

        /** Interstitials expire ~1h after load; a cached one older than this is discarded and reloaded. */
        const val AD_MAX_AGE_MS = 50 * 60_000L

        /** Backoff before each reload attempt after a failure; max attempts = size. */
        val RETRY_DELAYS_MS = longArrayOf(5_000L, 15_000L, 30_000L)

        /** Min gap between trigger-driven restarts after retries gave up. */
        const val TRIGGER_THROTTLE_MS = 10_000L

        /** Release placeholder IDs contain this (see app/build.gradle.kts). */
        const val PLACEHOLDER_MARK = "0000000000000000"
    }
}
