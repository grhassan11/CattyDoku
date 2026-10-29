package com.threetech.catpuzzle.data.ads

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
import com.threetech.catpuzzle.BuildConfig
import com.threetech.catpuzzle.data.platform.CurrentActivityProvider
import com.threetech.catpuzzle.di.IoDispatcher
import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.ads.AdShowResult
import com.threetech.catpuzzle.domain.ads.AdsRepository
import com.threetech.catpuzzle.domain.ads.AdsSdkStatus
import com.threetech.catpuzzle.domain.billing.AdsRemovedRepository
import com.threetech.catpuzzle.domain.consent.ConsentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
    private val consent: ConsentRepository,
    private val tracker: AdSessionTracker,
    private val adsRemoved: AdsRemovedRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AdsRepository, AdsSdkStatus {

    private val initMutex = Mutex()

    private val _initialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _initialized

    @Volatile private var lastPreloadFailed = false

    /** True while the interstitial preloader is running (main thread only). */
    private var preloading = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
            MetaConsentSeam.apply(consent.state.value.canRequestAds)
            // Background thread per Google guidance. Adapters (Meta, Unity) auto-initialise inside this call.
            withContext(io) {
                MobileAds.initialize(context, InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID).build()) {
                    // adapter initialisation complete (not awaited: bidding adapters must not delay the first ad)
                }
            }
            _initialized.value = true
            // Remove Ads: do not request interstitials at all. The collector starts / stops the preloader when the
            // entitlement flips at runtime (bought, refunded, subscription lapsed).
            scope.launch {
                adsRemoved.adsRemoved.collect { removed ->
                    if (removed) stopPreload() else startPreload()
                }
            }
        }
    }

    private fun stopPreload() {
        if (!preloading) return
        preloading = false
        try {
            InterstitialAdPreloader.destroy(unitId)
        } catch (t: Throwable) {
            Log.w(TAG, "interstitial preloader destroy failed", t)
        }
    }

    private fun startPreload() {
        if (preloading) return
        preloading = true
        lastPreloadFailed = false
        val request = AdRequest.Builder(unitId).build()
        val config = PreloadConfiguration(request)
        InterstitialAdPreloader.start(
            unitId,
            config,
            object : PreloadCallback {
                override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) {
                    lastPreloadFailed = false
                    dbg("preloaded")
                }

                override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) {
                    lastPreloadFailed = true
                    dbg("preload failed: $adError")
                }

                override fun onAdsExhausted(preloadId: String) = Unit
            },
        )
    }

    override suspend fun showIfReady(loadWaitTimeoutMs: Long): AdShowResult {
        if (!_initialized.value) {
            dbg("showIfReady NOT_READY: SDK not initialized (idsConfigured=$idsConfigured)")
            return AdShowResult.NOT_READY
        }
        return try {
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
                            if (cont.isActive) cont.resume(AdShowResult.SHOWN)
                        }

                        override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                            dbg("showIfReady FAILED: onAdFailedToShowFullScreenContent $fullScreenContentError")
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

        /** Release placeholder IDs contain this (see app/build.gradle.kts). */
        const val PLACEHOLDER_MARK = "0000000000000000"
    }
}
