package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.ads.AdFormat
import com.threetech.catpuzzle.domain.ads.AdFreePolicy
import com.threetech.catpuzzle.domain.ads.AdPolicyConfig
import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.ads.NativeAdHandle
import com.threetech.catpuzzle.domain.ads.NativeAdRepository
import com.threetech.catpuzzle.domain.billing.AdsRemovedRepository
import com.threetech.catpuzzle.domain.billing.NoAdsRemoved
import com.threetech.catpuzzle.domain.consent.ConsentRepository
import kotlinx.coroutines.CancellationException

/** Load the Home native ad if policy + consent allow. Null = show nothing. Never throws. */
class LoadNativeAd(
    private val consent: ConsentRepository,
    private val repository: NativeAdRepository,
    private val config: AdPolicyConfig,
    private val adsRemoved: AdsRemovedRepository = NoAdsRemoved,
) {
    /** Single policy gate: false = no slot at all (kill switch, Remove Ads, no consent, SDK off / placeholder ID). */
    fun isAllowed(): Boolean {
        if (!config.enabled || !config.nativeHomeEnabled) return false
        // Remove Ads: never even request the ad.
        if (AdFreePolicy.isSuppressedByPurchase(AdFormat.NATIVE_HOME, adsRemoved.adsRemoved.value)) return false
        if (!consent.state.value.canRequestAds) return false
        return repository.isConfigured
    }

    suspend operator fun invoke(): NativeAdHandle? {
        if (!isAllowed()) return null
        return try {
            repository.load()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }
    }
}

/** Home reached: the start flow (Splash, Terms, consent, notification prompt, Tutorial) is over, app-open may now fire. */
class MarkStartFlowComplete(private val tracker: AdSessionTracker) {
    operator fun invoke() {
        tracker.startFlowComplete = true
    }
}
