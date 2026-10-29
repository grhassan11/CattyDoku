package com.threetech.catpuzzle.domain.ads

import com.threetech.catpuzzle.domain.billing.Entitlement
import com.threetech.catpuzzle.domain.billing.FakeAdsRemovedRepository
import com.threetech.catpuzzle.domain.consent.ConsentRepository
import com.threetech.catpuzzle.domain.consent.ConsentState
import com.threetech.catpuzzle.domain.model.AppFlags
import com.threetech.catpuzzle.domain.usecase.LoadBannerAd
import com.threetech.catpuzzle.domain.usecase.LoadNativeAd
import com.threetech.catpuzzle.domain.usecase.ObserveAdSurfaces
import com.threetech.catpuzzle.ui.screens.game.FakeAppFlagsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ad-suppression matrix for a bought "Remove ads": native / banner / interstitial / app-open off, rewarded untouched. */
class AdsRemovedPolicyTest {

    // ---------------------------------------------------------------- the one rule

    @Test
    fun everyFormatExceptRewardedIsSuppressedWhenAdsRemoved() {
        for (format in AdFormat.entries) {
            val suppressed = AdFreePolicy.isSuppressedByPurchase(format, adsRemoved = true)
            assertEquals(format.name, format != AdFormat.REWARDED, suppressed)
            assertFalse("nothing suppressed without a purchase: $format", AdFreePolicy.isSuppressedByPurchase(format, false))
        }
    }

    // ---------------------------------------------------------------- interstitial

    private fun interstitialCtx(adsRemoved: Boolean) = InterstitialContext(
        trigger = AdTrigger.LEVEL_WON_CONTINUE,
        canRequestAds = true,
        level = 20,
        winsSinceLastAd = 2,
        nowMs = 1_000_000L,
        sessionStartMs = 0L,
        lastAdShownMs = null,
        lastInterstitialLevel = 0,
        adsShownThisSession = 0,
        adsRemoved = adsRemoved,
    )

    @Test
    fun interstitialShowsWithoutPurchase() {
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(interstitialCtx(adsRemoved = false)))
    }

    @Test
    fun interstitialNeverShowsWhenAdsRemoved() {
        assertEquals(
            AdDecision.Skip(AdSkipReason.ADS_REMOVED),
            InterstitialPolicy.decide(interstitialCtx(adsRemoved = true)),
        )
        // Also the optional lose-retry boundary.
        val retry = interstitialCtx(true).copy(trigger = AdTrigger.LOSE_RETRY)
        assertEquals(
            AdDecision.Skip(AdSkipReason.ADS_REMOVED),
            InterstitialPolicy.decide(retry, AdPolicyConfig(showOnLoseRetry = true)),
        )
    }

    // ---------------------------------------------------------------- app open

    private fun appOpenCtx(adsRemoved: Boolean) = AppOpenContext(
        coldStart = false,
        canRequestAds = true,
        sdkInitialized = true,
        startFlowComplete = true,
        fullScreenAdShowing = false,
        consentFormShowing = false,
        returnedFromAd = false,
        nowMs = 1_000_000L,
        backgroundedAtMs = 0L,
        lastAdShownMs = null,
        appOpenShownThisSession = 0,
        adAgeMs = 1_000L,
        playerLevel = 20,
        adsRemoved = adsRemoved,
    )

    @Test
    fun appOpenShowsWithoutPurchase() {
        assertEquals(AppOpenDecision.Show, AppOpenPolicy.decide(appOpenCtx(adsRemoved = false)))
    }

    @Test
    fun appOpenNeverShowsWhenAdsRemoved() {
        assertEquals(
            AppOpenDecision.Skip(AppOpenSkipReason.ADS_REMOVED),
            AppOpenPolicy.decide(appOpenCtx(adsRemoved = true)),
        )
    }

    // ---------------------------------------------------------------- game surfaces: banner off, rewarded on

    private class Consent(allow: Boolean = true) : ConsentRepository {
        override val state: StateFlow<ConsentState> = MutableStateFlow(ConsentState(canRequestAds = allow))
        override suspend fun gatherConsent() = Unit
        override suspend fun showPrivacyOptions() = Unit
    }

    private class Sdk : AdsSdkStatus {
        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
    }

    private class Rewarded : RewardedAdRepository {
        override val isConfigured = true
        override val loadState: StateFlow<RewardedLoadState> = MutableStateFlow(RewardedLoadState.READY)
        override fun preload() = Unit
        override suspend fun show(): RewardedResult = RewardedResult.EARNED
    }

    private class Handle : BannerAdHandle {
        override fun destroy() = Unit
    }

    private class Banner : BannerAdRepository {
        var loads = 0
        override val isConfigured = true
        override suspend fun load(widthDp: Int): BannerAdHandle? {
            loads++
            return Handle()
        }
    }

    private class NativeHandle : NativeAdHandle {
        override fun destroy() = Unit
    }

    private class Native : NativeAdRepository {
        var loads = 0
        override suspend fun load(): NativeAdHandle? {
            loads++
            return NativeHandle()
        }
    }

    private fun removed(on: Boolean) =
        FakeAdsRemovedRepository(if (on) Entitlement(foreverOwned = true, verifiedAtMs = 1L) else Entitlement.NONE) { 1L }

    @Test
    fun surfacesBannerOffRewardedOnWhenAdsRemoved() = runTest {
        val surfaces = ObserveAdSurfaces(
            FakeAppFlagsRepository(AppFlags(lastPlayedLevel = 20)), Consent(), Sdk(), Rewarded(), Banner(), AdPolicyConfig(),
            removed(true),
        )(MutableStateFlow(20)).first()
        assertFalse("banner suppressed", surfaces.banner)
        assertTrue("rewarded unaffected", surfaces.rewarded)
        assertTrue(surfaces.adsRemoved)
    }

    @Test
    fun surfacesBannerOnWithoutPurchase() = runTest {
        val surfaces = ObserveAdSurfaces(
            FakeAppFlagsRepository(AppFlags(lastPlayedLevel = 20)), Consent(), Sdk(), Rewarded(), Banner(), AdPolicyConfig(),
            removed(false),
        )(MutableStateFlow(20)).first()
        assertTrue(surfaces.banner)
        assertTrue(surfaces.rewarded)
        assertFalse(surfaces.adsRemoved)
    }

    @Test
    fun surfacesFlipLiveWhenEntitlementArrives() = runTest {
        val ads = removed(false)
        val flow = ObserveAdSurfaces(
            FakeAppFlagsRepository(AppFlags(lastPlayedLevel = 20)), Consent(), Sdk(), Rewarded(), Banner(), AdPolicyConfig(), ads,
        )(MutableStateFlow(20))
        assertTrue(flow.first().banner)
        ads.apply(Entitlement(foreverOwned = true, verifiedAtMs = 1L))
        assertFalse(flow.first().banner)
    }

    // ---------------------------------------------------------------- loaders request nothing

    @Test
    fun bannerIsNeverRequestedWhenAdsRemoved() = runTest {
        val repo = Banner()
        assertNull(LoadBannerAd(Consent(), Sdk(), repo, AdPolicyConfig(), removed(true))(30, 360))
        assertEquals(0, repo.loads)
        assertNotNull(LoadBannerAd(Consent(), Sdk(), repo, AdPolicyConfig(), removed(false))(30, 360))
        assertEquals(1, repo.loads)
    }

    @Test
    fun nativeIsNeverRequestedWhenAdsRemoved() = runTest {
        val repo = Native()
        assertNull(LoadNativeAd(Consent(), repo, AdPolicyConfig(), removed(true))())
        assertEquals(0, repo.loads)
        assertNotNull(LoadNativeAd(Consent(), repo, AdPolicyConfig(), removed(false))())
        assertEquals(1, repo.loads)
    }
}
