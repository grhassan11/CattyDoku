package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.ads.AdFormat
import com.threetech.catpuzzle.domain.ads.AdFreePolicy
import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.ads.AdShowResult
import com.threetech.catpuzzle.domain.ads.AdsSdkStatus
import com.threetech.catpuzzle.domain.ads.AppOpenAdRepository
import com.threetech.catpuzzle.domain.ads.AppOpenConfig
import com.threetech.catpuzzle.domain.ads.AppOpenContext
import com.threetech.catpuzzle.domain.ads.AppOpenDecision
import com.threetech.catpuzzle.domain.ads.AppOpenPolicy
import com.threetech.catpuzzle.domain.ads.AppOpenSkipReason
import com.threetech.catpuzzle.domain.billing.AdsRemovedRepository
import com.threetech.catpuzzle.domain.billing.NoAdsRemoved
import com.threetech.catpuzzle.domain.consent.ConsentRepository
import com.threetech.catpuzzle.domain.repository.AppFlagsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Start loading the next app-open ad when SDK is up and consent allows. Idempotent, never throws. */
class PreloadAppOpenAd(
    private val consent: ConsentRepository,
    private val sdk: AdsSdkStatus,
    private val config: AppOpenConfig,
    private val repository: AppOpenAdRepository,
    private val adsRemoved: AdsRemovedRepository = NoAdsRemoved,
) {
    operator fun invoke() {
        try {
            if (!config.enabled) return
            // Remove Ads: never request an app-open ad.
            if (AdFreePolicy.isSuppressedByPurchase(AdFormat.APP_OPEN, adsRemoved.adsRemoved.value)) return
            if (!consent.state.value.canRequestAds || !sdk.isInitialized.value) return
            repository.preload()
        } catch (t: Throwable) {
            // fail open
        }
    }
}

/**
 * Resume-only app-open. Called by the process-lifecycle observer on every foreground; the observer reports whether this
 * is the cold-start foreground, when the app was backgrounded, and whether the user left via an ad click.
 * Decision is the pure [AppOpenPolicy]. Coherence with interstitials via [AdSessionTracker]: a shown app-open starts the
 * shared cool-down (interstitial min interval) but does not touch the every-Nth-win counter or the interstitial cap, and
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
    private val flags: AppFlagsRepository,
    private val adsRemoved: AdsRemovedRepository = NoAdsRemoved,
) {
    suspend operator fun invoke(coldStart: Boolean, backgroundedAtMs: Long?, returnedFromAd: Boolean): Boolean {
        return try {
            val ctx = AppOpenContext(
                coldStart = coldStart,
                canRequestAds = consent.state.value.canRequestAds,
                sdkInitialized = sdk.isInitialized.value,
                startFlowComplete = tracker.startFlowComplete,
                fullScreenAdShowing = tracker.fullScreenAdShowing,
                consentFormShowing = tracker.consentFormShowing,
                returnedFromAd = returnedFromAd,
                nowMs = tracker.now(),
                backgroundedAtMs = backgroundedAtMs,
                lastAdShownMs = tracker.lastAdShownMs,
                appOpenShownThisSession = tracker.appOpenShown,
                adAgeMs = repository.readyAdAgeMs(),
                // Early-level gate: last played level = persisted progress (0 = nothing played yet = ad-free).
                playerLevel = flags.flags.first().lastPlayedLevel,
                adsRemoved = adsRemoved.adsRemoved.value,
            )
            when (val decision = AppOpenPolicy.decide(ctx, config)) {
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
        if (result == AdShowResult.SHOWN) tracker.recordAppOpenShown()
        preload()
        return result == AdShowResult.SHOWN
    }
}
