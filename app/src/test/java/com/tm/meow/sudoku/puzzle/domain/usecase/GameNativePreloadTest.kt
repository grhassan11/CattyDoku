package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicyConfig
import com.tm.meow.sudoku.puzzle.domain.ads.GameNativeAdCache
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdHandle
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdRepository
import com.tm.meow.sudoku.puzzle.domain.billing.AdsRemovedRepository
import com.tm.meow.sudoku.puzzle.domain.billing.Entitlement
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentState
import com.tm.meow.sudoku.puzzle.domain.network.ConnectivityObserver
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeAppFlagsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GameNativePreloadTest {

    private class Handle : NativeAdHandle {
        override fun destroy() = Unit
    }

    private class Repo : NativeAdRepository {
        var loads = 0
        override suspend fun load(): NativeAdHandle? {
            loads++
            return Handle()
        }
    }

    private class Consent(allow: Boolean = true) : ConsentRepository {
        override val state = MutableStateFlow(ConsentState(canRequestAds = allow))
        override suspend fun gatherConsent() = Unit
        override suspend fun showPrivacyOptions() = Unit
    }

    private class Removed(removed: Boolean = false) : AdsRemovedRepository {
        override val adsRemoved = MutableStateFlow(removed)
        override val entitlement: StateFlow<Entitlement> = MutableStateFlow(Entitlement.NONE)
        override suspend fun apply(entitlement: Entitlement) = Unit
    }

    private class Net(online: Boolean = true) : ConnectivityObserver {
        override val isOnline = MutableStateFlow(online)
    }

    private class Rig(
        consent: Consent = Consent(),
        removed: Removed = Removed(),
        net: Net = Net(),
        config: AdPolicyConfig = AdPolicyConfig(),
        lastPlayedLevel: Int = 50,
    ) {
        val repo = Repo()
        val time = FakeTimeProvider(now = 1_000_000L)
        val cache = GameNativeAdCache(time, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), removed)
        val flags = FakeAppFlagsRepository(com.tm.meow.sudoku.puzzle.domain.model.AppFlags(lastPlayedLevel = lastPlayedLevel))
        val loadNative = LoadNativeAd(consent, repo, config, removed, net)
        val preload = PreloadGameNativeAd(loadNative, cache, flags)
        val take = TakeGameNativeAd(loadNative, cache)
    }

    @Test
    fun allowedPreloadRequestsOnceAndTakeReturnsIt() = runTest {
        val rig = Rig()
        rig.preload(AdPlacement.SUDOKU_GAME)
        rig.preload(AdPlacement.SUDOKU_GAME)
        assertEquals(1, rig.repo.loads)
        assertNotNull(rig.take(AdPlacement.SUDOKU_GAME, playerLevel = 50))
        assertEquals("take did not hit the SDK", 1, rig.repo.loads)
        assertNull("take-once", rig.take(AdPlacement.SUDOKU_GAME, playerLevel = 50))
    }

    @Test
    fun noConsentBlocksPreload() = runTest {
        val rig = Rig(consent = Consent(allow = false))
        rig.preload(AdPlacement.LEVELS_GAME)
        rig.preload(AdPlacement.SUDOKU_GAME)
        assertEquals(0, rig.repo.loads)
    }

    @Test
    fun adsRemovedBlocksPreload() = runTest {
        val rig = Rig(removed = Removed(removed = true))
        rig.preload(AdPlacement.LEVELS_GAME)
        rig.preload(AdPlacement.SUDOKU_GAME)
        assertEquals(0, rig.repo.loads)
    }

    @Test
    fun offlineBlocksPreload() = runTest {
        val rig = Rig(net = Net(online = false))
        rig.preload(AdPlacement.SUDOKU_GAME)
        assertEquals(0, rig.repo.loads)
    }

    @Test
    fun remoteSwitchOffBlocksOnlyThatPlacement() = runTest {
        val rig = Rig(config = AdPolicyConfig(sudokuGameNativeShow = false))
        rig.preload(AdPlacement.SUDOKU_GAME)
        assertEquals(0, rig.repo.loads)
        rig.preload(AdPlacement.LEVELS_GAME)
        assertEquals(1, rig.repo.loads)
    }

    @Test
    fun lowLevelDoesNotBlockLevelsOrSudokuPreload() = runTest {
        val rig = Rig(lastPlayedLevel = 1)
        rig.preload(AdPlacement.LEVELS_GAME)
        assertEquals(1, rig.repo.loads)
        rig.preload(AdPlacement.SUDOKU_GAME)
        assertEquals(2, rig.repo.loads)
    }

    @Test
    fun homePlacementsAreNeverPreloadedAsGameAds() = runTest {
        val rig = Rig()
        rig.preload(AdPlacement.LEVELS_HOME)
        rig.preload(AdPlacement.SUDOKU_HOME)
        assertEquals(0, rig.repo.loads)
    }

    @Test
    fun takeServesCachedAdFromLevelOne() = runTest {
        val rig = Rig()
        rig.preload(AdPlacement.LEVELS_GAME)
        assertNotNull("level 1: served", rig.take(AdPlacement.LEVELS_GAME, playerLevel = 1))
    }

    @Test
    fun takeIsGatedByRemoveAdsEvenWhenAnAdIsCached() = runTest {
        val removed = Removed()
        val rig = Rig(removed = removed)
        rig.preload(AdPlacement.LEVELS_GAME)
        removed.adsRemoved.value = true
        assertNull(rig.take(AdPlacement.LEVELS_GAME, playerLevel = 1))
    }
}
