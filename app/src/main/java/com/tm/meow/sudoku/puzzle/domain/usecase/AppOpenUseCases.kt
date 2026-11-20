package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.startup.SplashTiming
import com.tm.meow.sudoku.puzzle.domain.network.AlwaysOnline
import com.tm.meow.sudoku.puzzle.domain.network.ConnectivityObserver
import com.tm.meow.sudoku.puzzle.domain.ads.AdFormat
import com.tm.meow.sudoku.puzzle.domain.ads.AdFreePolicy
import com.tm.meow.sudoku.puzzle.domain.ads.AdLogger
import com.tm.meow.sudoku.puzzle.domain.ads.NoopAdLogger
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.AdShowResult
import com.tm.meow.sudoku.puzzle.domain.ads.AdsSdkStatus
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenAdRepository
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenConfig
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenContext
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenDecision
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenPolicy
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenSkipReason
import com.tm.meow.sudoku.puzzle.domain.billing.AdsRemovedRepository
import com.tm.meow.sudoku.puzzle.domain.billing.NoAdsRemoved
import com.tm.meow.sudoku.puzzle.domain.config.NoopRemoteConfigRepository
import com.tm.meow.sudoku.puzzle.domain.config.RemoteConfigRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Start loading the next app-open ad when SDK is up and consent allows. Idempotent, never throws. */
class PreloadAppOpenAd(
    private val consent: ConsentRepository,
    private val sdk: AdsSdkStatus,
    private val repository: AppOpenAdRepository,
    private val adsRemoved: AdsRemovedRepository = NoAdsRemoved,
    private val connectivity: ConnectivityObserver = AlwaysOnline,
    private val logger: AdLogger = NoopAdLogger,
) {
    operator fun invoke() {
        try {
            // Remove Ads: never request an app-open ad.
            if (AdFreePolicy.isSuppressedByPurchase(AdFormat.APP_OPEN, adsRemoved.adsRemoved.value)) {
                logger.log("app-open preload: skip reason=ADS_REMOVED")
                return
            }
            if (!connectivity.isOnline.value) {
                logger.log("app-open preload: skip reason=OFFLINE")
                return
            }
            if (!consent.state.value.canRequestAds) {
                logger.log("app-open preload: skip reason=NO_CONSENT")
                return
            }
            if (!sdk.isInitialized.value) {
                logger.log("app-open preload: skip reason=SDK_NOT_READY")
                return
            }
            repository.preload()
        } catch (t: Throwable) {
            logger.log("app-open preload: threw $t")
            // fail open
        }
    }
}

/**
 * Resume-only app-open. Called by the process-lifecycle observer on every foreground; the observer reports whether this
 * is the cold-start foreground. No discretionary gates (user order).
 * Decision is the pure [AppOpenPolicy]. The foreground right after an ad click-out (browser / store) is skipped once
 * ([AdSessionTracker.consumeAdClicked]). Coherence with interstitials via [AdSessionTracker]: a shown app-open sets the
 * shared last-shown time (read by Rate us) but does not touch the every-Nth-win counter or the interstitial cap, and
 * an on-screen interstitial blocks it. After a show (or any skip that leaves no fresh ad) the next ad is preloaded.
 * Returns true when an ad was shown. Never throws.
 */
class ShowAppOpenIfAllowed(
    private val consent: ConsentRepository,
    private val sdk: AdsSdkStatus,
    private val repository: AppOpenAdRepository,
    private val tracker: AdSessionTracker,
    private val config: AppOpenConfig,
    private val preload: PreloadAppOpenAd,
    private val adsRemoved: AdsRemovedRepository = NoAdsRemoved,
    private val connectivity: ConnectivityObserver = AlwaysOnline,
    private val logger: AdLogger = NoopAdLogger,
) {
    suspend operator fun invoke(coldStart: Boolean, fromSplash: Boolean = false): Boolean {
        return try {
            val externalReturn = !fromSplash && tracker.consumeExternalLaunch()
            val ctx = AppOpenContext(
                coldStart = coldStart,
                fromSplash = fromSplash,
                returnFromExternalLaunch = externalReturn,
                canRequestAds = consent.state.value.canRequestAds,
                sdkInitialized = sdk.isInitialized.value,
                fullScreenAdShowing = tracker.fullScreenAdShowing,
                consentFormShowing = tracker.consentFormShowing,
                adAgeMs = repository.readyAdAgeMs(),
                adsRemoved = adsRemoved.adsRemoved.value,
                online = connectivity.isOnline.value,
                // One-shot: consumed on every foreground so the flag cannot stick; the splash path never consumes.
                returnFromAdClick = !fromSplash && tracker.consumeAdClicked(),
                suppressedScreen = tracker.appOpenSuppressedScreen,
            )
            val decision = AppOpenPolicy.decide(ctx, config)
            logger.log("app-open gate: coldStart=$coldStart fromSplash=$fromSplash -> $decision ctx=$ctx")
            when (decision) {
                AppOpenDecision.Show -> showNow()
                is AppOpenDecision.Skip -> {
                    if (decision.reason == AppOpenSkipReason.AD_EXPIRED || decision.reason == AppOpenSkipReason.ADS_REMOVED) {
                        repository.discard() // expired, or no longer allowed (Remove Ads bought while one was held)
                    }
                    // A skipped foreground must still leave a fresh ad ready for the next one.
                    preload()
                    false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logger.log("app-open gate: threw $t")
            false
        }
    }

    private suspend fun showNow(): Boolean {
        tracker.fullScreenAdShowing = true
        val result = try {
            repository.show()
        } finally {
            tracker.fullScreenAdShowing = false
        }
        logger.log("app-open show result=$result")
        if (result == AdShowResult.SHOWN) tracker.recordAppOpenShown()
        preload()
        return result == AdShowResult.SHOWN
    }
}

/**
 * Splash app-open: called once after the other splash work is done (so the wait is not part of SplashTiming.MAX_MS).
 * Starts / joins the app-open load, waits up to `waitMs` (per call; default [loadWaitTimeoutMs] = [SplashTiming.APP_OPEN_WAIT_MS], 5 s = floor;
 * the splash passes the remainder of [SplashTiming.APP_OPEN_WAIT_FIRST_MS], 15 s, for first and returning launches alike) for it to finish, then shows through [ShowAppOpenIfAllowed] with the cold-start
 * skip lifted. The wait returns immediately when an ad is ready, or at once when gated (Remove Ads / offline / no consent / SDK off: no load can start). A failed / no-fill load is retried (1 s back-off) until the budget runs out: the timeout is the only reason an eligible launch ends without the ad. Suspends until the ad is dismissed / failed, bounded by [SHOW_GUARD_MS] so navigation can never hang.
 * A shown ad goes through the normal path: [AdSessionTracker.recordAppOpenShown] sets last-shown (shared cool-down).
 * Returns true when an ad was shown. Never throws; every failure / skip = false = continue without ad.
 */
class ShowAppOpenOnSplash(
    private val repository: AppOpenAdRepository,
    private val preload: PreloadAppOpenAd,
    private val show: ShowAppOpenIfAllowed,
    private val loadWaitTimeoutMs: Long = DEFAULT_LOAD_WAIT_MS,
    private val logger: AdLogger = NoopAdLogger,
    private val remoteConfig: RemoteConfigRepository = NoopRemoteConfigRepository,
) {
    /**
     * Remote Config kill switch `splash_app_open_ad_show`. Synchronous read of the latest activated snapshot (cached /
     * default value); never awaits a fetch. Fails open (true) on any error. Splash only; lifecycle app-open ignores it.
     */
    fun isEnabled(): Boolean = try {
        remoteConfig.current().splashAppOpenAdShow
    } catch (t: Throwable) {
        true
    }

    /** [waitMs]: max load wait for this call (first launch passes up to 15 s); defaults to the constructor value. */
    suspend operator fun invoke(waitMs: Long = loadWaitTimeoutMs): Boolean {
        return try {
            if (!isEnabled()) {
                logger.log("splash-app-open: skip reason=RC_DISABLED")
                return false
            }
            logger.log("splash-app-open: start ready=${repository.readyAdAgeMs() != null} loading=${repository.isLoading()}")
            preload() // idempotent: joins an in-flight lifecycle-observer preload; no-op when gated
            val waitOk = withTimeoutOrNull(waitMs) {
                // Gated (Remove Ads / offline / no consent / SDK off): no load started, nothing to wait for.
                if (repository.readyAdAgeMs() == null && !repository.isLoading()) return@withTimeoutOrNull true
                // Otherwise keep going until an ad is ready: a failed / no-fill load is retried after a short back-off, so the
                // only way to leave without an ad is the [waitMs] timeout (or a gate that stops the retry).
                while (repository.readyAdAgeMs() == null) {
                    if (repository.isLoading()) {
                        delay(POLL_MS)
                    } else {
                        delay(RETRY_BACKOFF_MS)
                        if (repository.readyAdAgeMs() != null) break
                        preload()
                        if (repository.readyAdAgeMs() == null && !repository.isLoading()) break // gated now: stop retrying
                    }
                }
                true
            }
            logger.log(
                "splash-app-open: wait done timedOut=${waitOk == null} ready=${repository.readyAdAgeMs() != null} " +
                    "loading=${repository.isLoading()}",
            )
            // null = guard hit (dismiss never reported): give up waiting and move on
            val shown = withTimeoutOrNull(SHOW_GUARD_MS) { show(coldStart = true, fromSplash = true) }
            logger.log("splash-app-open: end shown=$shown" + if (shown == null) " (show guard hit)" else "")
            shown ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logger.log("splash-app-open: skip reason=EXCEPTION $t")
            false
        }
    }

    companion object {
        const val POLL_MS = 50L

        /** Pause before re-requesting after a failed / no-fill load, so a no-fill never hammers the ad server. */
        const val RETRY_BACKOFF_MS = 1_000L

        /** Splash-specific wait (5 s); deliberately NOT AdPolicyConfig.loadWaitTimeoutMs (3 s, interstitial). */
        const val DEFAULT_LOAD_WAIT_MS = SplashTiming.APP_OPEN_WAIT_MS

        /** Upper bound for the whole show (SDK start watchdog is 8 s; video ads run up to ~30 s). */
        const val SHOW_GUARD_MS = 60_000L
    }
}
