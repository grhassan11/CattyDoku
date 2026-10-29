package com.threetech.catpuzzle.ui.ads

import app.cash.turbine.test
import com.threetech.catpuzzle.domain.ads.AdPolicyConfig
import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.ads.AdShowResult
import com.threetech.catpuzzle.domain.ads.AdsRepository
import com.threetech.catpuzzle.domain.ads.AdsSdkStatus
import com.threetech.catpuzzle.domain.ads.BannerAdHandle
import com.threetech.catpuzzle.domain.ads.BannerAdRepository
import com.threetech.catpuzzle.domain.ads.RewardedAdRepository
import com.threetech.catpuzzle.domain.ads.RewardedLoadState
import com.threetech.catpuzzle.domain.ads.RewardedResult
import com.threetech.catpuzzle.domain.consent.ConsentRepository
import com.threetech.catpuzzle.domain.consent.ConsentState
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.usecase.FakeInventoryRepository
import com.threetech.catpuzzle.domain.usecase.FakeTimeProvider
import com.threetech.catpuzzle.domain.usecase.GrantBoosterReward
import com.threetech.catpuzzle.domain.usecase.LoadBannerAd
import com.threetech.catpuzzle.domain.usecase.ObserveAdSurfaces
import com.threetech.catpuzzle.domain.usecase.PreloadRewardedAd
import com.threetech.catpuzzle.domain.usecase.RecordLevelWon
import com.threetech.catpuzzle.domain.usecase.ShowInterstitialIfAllowed
import com.threetech.catpuzzle.domain.usecase.ShowRewardedAd
import com.threetech.catpuzzle.ui.screens.game.FakeAppFlagsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class AvConsent : ConsentRepository {
    override val state: StateFlow<ConsentState> = MutableStateFlow(ConsentState(canRequestAds = true))
    override suspend fun gatherConsent() = Unit
    override suspend fun showPrivacyOptions() = Unit
}

private class AvSdk : AdsSdkStatus {
    override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
}

private class AvInterstitial : AdsRepository {
    var showCalls = 0
    override suspend fun initialize() = Unit
    override suspend fun showIfReady(loadWaitTimeoutMs: Long): AdShowResult {
        showCalls++
        return AdShowResult.SHOWN
    }
}

private class AvRewarded(var result: RewardedResult = RewardedResult.EARNED) : RewardedAdRepository {
    override val isConfigured = true
    override val loadState: StateFlow<RewardedLoadState> = MutableStateFlow(RewardedLoadState.READY)
    var showCalls = 0
    var preloadCalls = 0

    /** When set, show() suspends until completed (simulates the ad being on screen). */
    var gate: CompletableDeferred<Unit>? = null

    override fun preload() {
        preloadCalls++
    }

    override suspend fun show(): RewardedResult {
        showCalls++
        gate?.await()
        return result
    }
}

private class AvBannerHandle : BannerAdHandle {
    var destroyed = false
    override fun destroy() {
        destroyed = true
    }
}

private class AvBanner : BannerAdRepository {
    override val isConfigured = true
    var loadCalls = 0
    var last: AvBannerHandle? = null
    override suspend fun load(widthDp: Int): BannerAdHandle? {
        loadCalls++
        return AvBannerHandle().also { last = it }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdsViewModelTest {

    private class Rig {
        val time = FakeTimeProvider(now = 1_000_000L)
        val tracker = AdSessionTracker(time)
        val consent = AvConsent()
        val sdk = AvSdk()
        val flags = FakeAppFlagsRepository()
        val inventory = FakeInventoryRepository()
        val interstitial = AvInterstitial()
        val rewarded = AvRewarded()
        val banner = AvBanner()
        val config = AdPolicyConfig()

        fun viewModel() = AdsViewModel(
            recordLevelWon = RecordLevelWon(tracker),
            showInterstitial = ShowInterstitialIfAllowed(consent, interstitial, tracker, flags, config),
            observeAdSurfaces = ObserveAdSurfaces(flags, consent, sdk, rewarded, banner, config),
            preloadRewarded = PreloadRewardedAd(consent, sdk, rewarded, config),
            showRewarded = ShowRewardedAd(consent, sdk, rewarded, tracker, config),
            grantBoosterReward = GrantBoosterReward(inventory),
            loadBanner = LoadBannerAd(consent, sdk, banner, config),
            time = time,
        )
    }

    private fun adsTest(block: suspend kotlinx.coroutines.test.TestScope.(Rig, AdsViewModel) -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val rig = Rig()
            block(rig, rig.viewModel())
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ---------------------------------------------------------------- rewarded: booster

    @Test
    fun boosterRewardGrantedOnlyOnEarned() = adsTest { rig, vm ->
        vm.setScreenLevel(15)
        var granted = 0
        vm.watchBoosterAd(Booster.CAT) { granted++ }
        advanceUntilIdle()
        assertEquals(1, granted)
        assertEquals(1, rig.inventory.get(Booster.CAT))
        assertEquals(0, rig.inventory.get(Booster.HINT))
        assertFalse(vm.rewardedBusy.value)
    }

    @Test
    fun dismissWithoutRewardGrantsNothingAndTellsTheUser() = adsTest { rig, vm ->
        vm.setScreenLevel(15)
        rig.rewarded.result = RewardedResult.DISMISSED
        var granted = 0
        vm.events.test {
            vm.watchBoosterAd(Booster.HINT) { granted++ }
            assertEquals(AdsUiEvent.RewardNotEarned, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, granted)
        assertEquals(0, rig.inventory.get(Booster.HINT))
    }

    @Test
    fun notReadyOrFailedGrantsNothingAndEmitsUnavailable() = adsTest { rig, vm ->
        vm.setScreenLevel(15)
        for (res in listOf(RewardedResult.NOT_READY, RewardedResult.FAILED)) {
            rig.rewarded.result = res
            var granted = 0
            vm.events.test {
                vm.watchBoosterAd(Booster.CAT) { granted++ }
                assertEquals(AdsUiEvent.RewardedUnavailable, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals("$res", 0, granted)
        }
        assertEquals(0, rig.inventory.get(Booster.CAT))
    }

    @Test
    fun adFreeLevelNeverShowsARewardedAd() = adsTest { rig, vm ->
        vm.setScreenLevel(9)
        var granted = 0
        vm.watchBoosterAd(Booster.CAT) { granted++ }
        advanceUntilIdle()
        assertEquals(0, rig.rewarded.showCalls)
        assertEquals(0, granted)
        assertEquals(0, rig.inventory.get(Booster.CAT))
    }

    @Test
    fun secondTapWhileAdIsOnScreenIsIgnored() = adsTest { rig, vm ->
        vm.setScreenLevel(15)
        val gate = CompletableDeferred<Unit>()
        rig.rewarded.gate = gate
        var granted = 0
        vm.watchBoosterAd(Booster.CAT) { granted++ }
        assertTrue(vm.rewardedBusy.value)
        vm.watchBoosterAd(Booster.CAT) { granted++ }
        vm.watchReviveAd { granted++ }
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, rig.rewarded.showCalls)
        assertEquals(1, granted)
        assertEquals(1, rig.inventory.get(Booster.CAT))
        assertFalse(vm.rewardedBusy.value)
    }

    // ---------------------------------------------------------------- rewarded: revive

    @Test
    fun reviveCallbackOnlyOnEarned() = adsTest { rig, vm ->
        vm.setScreenLevel(15)
        var revived = 0
        rig.rewarded.result = RewardedResult.DISMISSED
        vm.watchReviveAd { revived++ }
        advanceUntilIdle()
        assertEquals(0, revived)
        rig.rewarded.result = RewardedResult.EARNED
        vm.watchReviveAd { revived++ }
        advanceUntilIdle()
        assertEquals(1, revived)
    }

    @Test
    fun rewardedShowUpdatesSharedFullScreenCooldown() = adsTest { rig, vm ->
        vm.setScreenLevel(15)
        assertNull(rig.tracker.lastAdShownMs)
        vm.watchReviveAd { }
        advanceUntilIdle()
        assertNotNull(rig.tracker.lastAdShownMs)
    }

    // ---------------------------------------------------------------- interstitial boundary

    @Test
    fun afterWinAlwaysContinuesAndIsAdFreeThroughLevelNine() = adsTest { rig, vm ->
        repeat(2) { vm.onLevelWon(isDaily = false) }
        rig.time.now += 200_000L
        var done = 0
        vm.afterWin(level = 9, isDaily = false) { done++ }
        advanceUntilIdle()
        assertEquals(1, done)
        assertEquals("level 9 is ad-free", 0, rig.interstitial.showCalls)
        vm.afterWin(level = 10, isDaily = false) { done++ }
        advanceUntilIdle()
        assertEquals(2, done)
        assertEquals(1, rig.interstitial.showCalls)
    }

    // ---------------------------------------------------------------- banner

    @Test
    fun bannerLoadsOnlyPastTheGateAndIsDestroyedOnRelease() = adsTest { rig, vm ->
        vm.setScreenLevel(9)
        vm.requestBanner(360)
        advanceUntilIdle()
        assertEquals(0, rig.banner.loadCalls)
        assertNull(vm.banner.value)

        vm.setScreenLevel(10)
        rig.time.now += 61_000L // past the retry-after-fail throttle
        vm.requestBanner(360)
        advanceUntilIdle()
        assertEquals(1, rig.banner.loadCalls)
        assertNotNull(vm.banner.value)
        vm.requestBanner(360) // already held
        assertEquals(1, rig.banner.loadCalls)

        val held = rig.banner.last!!
        vm.releaseBanner()
        assertTrue(held.destroyed)
        assertNull(vm.banner.value)
    }
}
