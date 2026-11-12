package com.tm.meow.sudoku.puzzle.domain.ads

import com.tm.meow.sudoku.puzzle.domain.billing.Entitlement
import com.tm.meow.sudoku.puzzle.domain.billing.FakeAdsRemovedRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentState
import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import com.tm.meow.sudoku.puzzle.domain.usecase.AdPlacement
import com.tm.meow.sudoku.puzzle.domain.usecase.LoadNativeAd
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveAdSurfaces
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeAppFlagsRepository
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

/** The ad-suppression matrix for a bought "Remove ads": native / interstitial / app-open off, rewarded untouched. */
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
        lastInterstitialLevel = 0,
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
        // Also the lose-retry boundary.
        val retry = interstitialCtx(true).copy(trigger = AdTrigger.LOSE_RETRY)
        assertEquals(
            AdDecision.Skip(AdSkipReason.ADS_REMOVED),
            InterstitialPolicy.decide(retry, AdPolicyConfig()),
        )
    }

    // ---------------------------------------------------------------- app open

    private fun appOpenCtx(adsRemoved: Boolean) = AppOpenContext(
        coldStart = false,
        canRequestAds = true,
        sdkInitialized = true,
        fullScreenAdShowing = false,
        consentFormShowing = false,
        adAgeMs = 1_000L,
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

    // ---------------------------------------------------------------- game surfaces: native off, rewarded on

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
    fun surfacesGameNativeOffRewardedOnWhenAdsRemoved() = runTest {
        val surfaces = ObserveAdSurfaces(
            FakeAppFlagsRepository(AppFlags(lastPlayedLevel = 20)), Consent(), Sdk(), Rewarded(), Native(), AdPolicyConfig(),
            removed(true),
        )(MutableStateFlow(20)).first()
        assertFalse("game native suppressed", surfaces.levelsGameNative)
        assertTrue("rewarded unaffected", surfaces.levelsBoosters && surfaces.levelsRevive && surfaces.sudokuBoosters && surfaces.sudokuRevive)
        assertTrue(surfaces.adsRemoved)
    }

    @Test
    fun surfacesGameNativeOnWithoutPurchase() = runTest {
        val surfaces = ObserveAdSurfaces(
            FakeAppFlagsRepository(AppFlags(lastPlayedLevel = 20)), Consent(), Sdk(), Rewarded(), Native(), AdPolicyConfig(),
            removed(false),
        )(MutableStateFlow(20)).first()
        assertTrue(surfaces.levelsGameNative)
        assertTrue(surfaces.levelsBoosters)
        assertFalse(surfaces.adsRemoved)
    }

    @Test
    fun sudokuGameNativeSuppressedWhenAdsRemoved() = runTest {
        fun surfaces(on: Boolean) = ObserveAdSurfaces(
            FakeAppFlagsRepository(AppFlags(lastPlayedLevel = 0)), Consent(), Sdk(), Rewarded(), Native(), AdPolicyConfig(),
            removed(on),
        )(MutableStateFlow(0))
        assertFalse(surfaces(true).first().sudokuGameNative)
        assertTrue(surfaces(false).first().sudokuGameNative)
        val repo = Native()
        assertNull(LoadNativeAd(Consent(), repo, AdPolicyConfig(), removed(true))(AdPlacement.SUDOKU_GAME, 0))
        assertEquals(0, repo.loads)
        assertNotNull(LoadNativeAd(Consent(), repo, AdPolicyConfig(), removed(false))(AdPlacement.SUDOKU_GAME, 0))
    }

    @Test
    fun surfacesFlipLiveWhenEntitlementArrives() = runTest {
        val ads = removed(false)
        val flow = ObserveAdSurfaces(
            FakeAppFlagsRepository(AppFlags(lastPlayedLevel = 20)), Consent(), Sdk(), Rewarded(), Native(), AdPolicyConfig(), ads,
        )(MutableStateFlow(20))
        assertTrue(flow.first().levelsGameNative)
        ads.apply(Entitlement(foreverOwned = true, verifiedAtMs = 1L))
        assertFalse(flow.first().levelsGameNative)
    }

    // ---------------------------------------------------------------- loaders request nothing

    @Test
    fun gameNativeIsNeverRequestedWhenAdsRemoved() = runTest {
        val repo = Native()
        assertNull(LoadNativeAd(Consent(), repo, AdPolicyConfig(), removed(true))(AdPlacement.LEVELS_GAME, 30))
        assertEquals(0, repo.loads)
        assertNotNull(LoadNativeAd(Consent(), repo, AdPolicyConfig(), removed(false))(AdPlacement.LEVELS_GAME, 30))
        assertEquals(1, repo.loads)
    }

    @Test
    fun nativeIsNeverRequestedWhenAdsRemoved() = runTest {
        val repo = Native()
        assertNull(LoadNativeAd(Consent(), repo, AdPolicyConfig(), removed(true))(AdPlacement.LEVELS_HOME))
        assertEquals(0, repo.loads)
        assertNotNull(LoadNativeAd(Consent(), repo, AdPolicyConfig(), removed(false))(AdPlacement.LEVELS_HOME))
        assertEquals(1, repo.loads)
    }
}
