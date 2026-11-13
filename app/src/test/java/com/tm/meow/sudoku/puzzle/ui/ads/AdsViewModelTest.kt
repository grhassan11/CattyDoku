package com.tm.meow.sudoku.puzzle.ui.ads

import app.cash.turbine.test
import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicyConfig
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.AdShowResult
import com.tm.meow.sudoku.puzzle.domain.ads.GameNativeAdCache
import com.tm.meow.sudoku.puzzle.domain.usecase.PreloadGameNativeAd
import com.tm.meow.sudoku.puzzle.domain.usecase.TakeGameNativeAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import com.tm.meow.sudoku.puzzle.domain.ads.AdsRepository
import com.tm.meow.sudoku.puzzle.domain.ads.AdsSdkStatus
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdHandle
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdRepository
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdSlotState
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedAdRepository
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedLoadState
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedResult
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentState
import com.tm.meow.sudoku.puzzle.domain.model.Booster
import com.tm.meow.sudoku.puzzle.domain.usecase.FakeInventoryRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.FakeTimeProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.GatherConsentAndInitAds
import com.tm.meow.sudoku.puzzle.domain.usecase.GrantBoosterReward
import com.tm.meow.sudoku.puzzle.domain.usecase.PreloadInterstitialAd
import com.tm.meow.sudoku.puzzle.domain.usecase.RetryAdsInit
import com.tm.meow.sudoku.puzzle.domain.usecase.AdPlacement
import com.tm.meow.sudoku.puzzle.domain.usecase.LoadNativeAd
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveAdSurfaces
import com.tm.meow.sudoku.puzzle.domain.usecase.PreloadRewardedAd
import com.tm.meow.sudoku.puzzle.domain.usecase.RecordLevelWon
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowInterstitialIfAllowed
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowRewardedAd
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeAppFlagsRepository
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

private class AvNativeHandle : NativeAdHandle {
    var destroyed = false
    override fun destroy() {
        destroyed = true
    }
}

private class AvNative : NativeAdRepository {
    override val isConfigured = true
    var loadCalls = 0
    var fail = false
    var last: AvNativeHandle? = null
    override suspend fun load(): NativeAdHandle? {
        loadCalls++
        if (fail) return null
        return AvNativeHandle().also { last = it }
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
        val nativeAds = AvNative()
        val config = AdPolicyConfig()
        val loadNative = LoadNativeAd(consent, nativeAds, config)
        val cache = GameNativeAdCache(time, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        val preloadGame = PreloadGameNativeAd(loadNative, cache, flags)

        fun viewModel() = AdsViewModel(
            recordLevelWon = RecordLevelWon(tracker),
            showInterstitial = ShowInterstitialIfAllowed(consent, interstitial, tracker, flags, config),
            preloadInterstitial = PreloadInterstitialAd(consent, interstitial, config),
            observeAdSurfaces = ObserveAdSurfaces(flags, consent, sdk, rewarded, nativeAds, config),
            preloadRewarded = PreloadRewardedAd(consent, sdk, rewarded, config),
            showRewarded = ShowRewardedAd(consent, sdk, rewarded, tracker, config),
            grantBoosterReward = GrantBoosterReward(inventory),
            loadNative = loadNative,
            takeGameNative = TakeGameNativeAd(loadNative, cache),
            preloadGameNative = preloadGame,
            time = time,
            retryAdsInit = RetryAdsInit(GatherConsentAndInitAds(consent, interstitial, flags, tracker)),
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
    fun earlyLevelNoLongerGatesRewardedAd() = adsTest { rig, vm ->
        vm.setScreenLevel(2)
        var granted = 0
        vm.watchBoosterAd(Booster.CAT) { granted++ }
        advanceUntilIdle()
        assertEquals(1, rig.rewarded.showCalls)
        assertEquals(1, granted)
        assertEquals(1, rig.inventory.get(Booster.CAT))
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
    fun afterWinAlwaysContinuesAndShowsEveryLevelIncludingDaily() = adsTest { rig, vm ->
        repeat(2) { vm.onLevelWon(isDaily = false) }
        rig.time.now += 200_000L
        var done = 0
        vm.afterWin(level = 4, isDaily = false) { done++ }
        advanceUntilIdle()
        assertEquals(1, done)
        assertEquals("default frequency 1: every level shows", 1, rig.interstitial.showCalls)
        vm.afterWin(level = 5, isDaily = false) { done++ }
        advanceUntilIdle()
        assertEquals(2, done)
        assertEquals(2, rig.interstitial.showCalls)
        vm.afterWin(level = 0, isDaily = true) { done++ }
        advanceUntilIdle()
        assertEquals(3, done)
        assertEquals("daily win shows too", 3, rig.interstitial.showCalls)
    }

    // ---------------------------------------------------------------- game native slot

    @Test
    fun gameNativeLoadsFromLevelOneAndIsDestroyedOnRelease() = adsTest { rig, vm ->
        vm.setScreenLevel(1)
        vm.requestGameNative(AdPlacement.LEVELS_GAME)
        advanceUntilIdle()
        assertEquals(1, rig.nativeAds.loadCalls)
        assertTrue(vm.gameNative.value is NativeAdSlotState.Loaded)
        vm.requestGameNative(AdPlacement.LEVELS_GAME) // already held
        assertEquals(1, rig.nativeAds.loadCalls)

        val held = rig.nativeAds.last!!
        vm.releaseGameNative()
        assertTrue(held.destroyed)
        assertEquals(NativeAdSlotState.Hidden, vm.gameNative.value)
    }

    @Test
    fun sudokuGameNativeHasNoLevelGateAndPreloadsTheNextOne() = adsTest { rig, vm ->
        vm.requestGameNative(AdPlacement.SUDOKU_GAME)
        advanceUntilIdle()
        assertEquals("live request + preload of the next game", 2, rig.nativeAds.loadCalls)
        assertTrue(vm.gameNative.value is NativeAdSlotState.Loaded)
        vm.releaseGameNative()
    }

    @Test
    fun failedGameNativeLoadIsThrottled() = adsTest { rig, vm ->
        rig.nativeAds.fail = true
        vm.requestGameNative(AdPlacement.SUDOKU_GAME)
        advanceUntilIdle()
        assertEquals(NativeAdSlotState.Failed, vm.gameNative.value)
        assertEquals(1, rig.nativeAds.loadCalls)
        vm.requestGameNative(AdPlacement.SUDOKU_GAME)
        advanceUntilIdle()
        assertEquals("throttled", 1, rig.nativeAds.loadCalls)
    }

    @Test
    fun preloadedGameNativeIsBoundWithoutALiveRequestAndTakenOnce() = adsTest { rig, vm ->
        rig.preloadGame(AdPlacement.SUDOKU_GAME) // what Sudoku entry does
        assertEquals(1, rig.nativeAds.loadCalls)
        val preloaded = rig.nativeAds.last!!

        vm.requestGameNative(AdPlacement.SUDOKU_GAME)
        advanceUntilIdle()
        val loaded = vm.gameNative.value as NativeAdSlotState.Loaded
        assertTrue("bound the preloaded ad", loaded.ad === preloaded)
        assertEquals("only the next-game preload hit the SDK", 2, rig.nativeAds.loadCalls)

        // The preloaded ad left the cache: releasing destroys it, the cache still holds only the NEXT one.
        vm.releaseGameNative()
        assertTrue(preloaded.destroyed)
        assertFalse(rig.nativeAds.last!!.destroyed)
        assertTrue("next ad available", rig.cache.take(AdPlacement.SUDOKU_GAME) != null)
        assertNull("take-once", rig.cache.take(AdPlacement.SUDOKU_GAME))
    }

    @Test
    fun expiredPreloadIsDestroyedAndReplacedByALiveRequest() = adsTest { rig, vm ->
        rig.preloadGame(AdPlacement.SUDOKU_GAME)
        val stale = rig.nativeAds.last!!
        rig.time.now += 56 * 60 * 1000L
        vm.requestGameNative(AdPlacement.SUDOKU_GAME)
        advanceUntilIdle()
        assertTrue("expired ad destroyed, never bound", stale.destroyed)
        val loaded = vm.gameNative.value as NativeAdSlotState.Loaded
        assertFalse(loaded.ad === stale)
        vm.releaseGameNative()
    }
}
