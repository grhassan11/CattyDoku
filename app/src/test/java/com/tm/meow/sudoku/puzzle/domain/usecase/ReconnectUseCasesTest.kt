package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.AdsRepository
import com.tm.meow.sudoku.puzzle.domain.ads.AdShowResult
import com.tm.meow.sudoku.puzzle.domain.config.AppRemoteConfig
import com.tm.meow.sudoku.puzzle.domain.config.RemoteConfigRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentState
import com.tm.meow.sudoku.puzzle.domain.network.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectUseCasesTest {

    private class Net(online: Boolean) : ConnectivityObserver {
        val flow = MutableStateFlow(online)
        override val isOnline: StateFlow<Boolean> = flow
    }

    /** Consent whose gather needs the network: offline leaves [tracker] pending (as the real impl does), online grants. */
    private class Consent(private val tracker: AdSessionTracker, private val net: Net, private val grant: Boolean = true) :
        ConsentRepository {
        private val _state = MutableStateFlow(ConsentState())
        override val state: StateFlow<ConsentState> = _state
        var gathers = 0

        override suspend fun gatherConsent() {
            gathers++
            tracker.consentGatherPending = true
            if (net.flow.value) {
                tracker.consentGatherPending = false
                _state.value = ConsentState(canRequestAds = grant)
            }
        }

        override suspend fun showPrivacyOptions() = Unit
    }

    private class Ads(private val failFirst: Boolean = false) : AdsRepository {
        var inits = 0
        var up = false
        override val isSdkInitialized: Boolean get() = up

        override suspend fun initialize() {
            inits++
            if (failFirst && inits == 1) return // stays uninitialised, like a failed MobileAds.initialize
            up = true
        }

        override fun preloadInterstitial() = Unit
        override suspend fun showIfReady(loadWaitTimeoutMs: Long): AdShowResult = AdShowResult.NOT_READY
    }

    private class Rc : RemoteConfigRepository {
        var refreshes = 0
        override fun current() = AppRemoteConfig()
        override fun refresh() {
            refreshes++
        }
    }

    private class Rig(online: Boolean, grant: Boolean = true, failFirstInit: Boolean = false) {
        val net = Net(online)
        val tracker = AdSessionTracker(FakeTimeProvider()).apply {
            startupGatherStarted = true
            startupGatherFinished = true
        }
        val consent = Consent(tracker, net, grant)
        val ads = Ads(failFirstInit)
        val rc = Rc()
        var preloads = 0
        val recover = RecoverAfterReconnect(
            consent, ads, tracker,
            RefreshRemoteConfig(rc) { consent.state.value.canRequestAds },
            preloadAds = { preloads++ },
            connectivity = net,
        )
    }

    @Test
    fun offlineStartThenOnline_regathersConsent_initsSdk_fetchesConfig_preloads() = runTest {
        val r = Rig(online = false)
        r.consent.gatherConsent() // startup gather while offline: skipped, pending
        assertTrue(r.tracker.consentGatherPending)
        assertFalse(r.recover.run()) // still offline: nothing happens
        assertEquals(0, r.ads.inits)

        r.net.flow.value = true
        assertTrue(r.recover.needsRecovery())
        assertTrue(r.recover.run())

        assertFalse(r.tracker.consentGatherPending)
        assertEquals(2, r.consent.gathers)
        assertEquals(1, r.ads.inits)
        assertEquals(1, r.rc.refreshes)
        assertEquals(1, r.preloads)
    }

    @Test
    fun consentDenied_noInit_noConfigFetch_noPreload() = runTest {
        val r = Rig(online = false, grant = false)
        r.consent.gatherConsent()
        r.net.flow.value = true
        assertTrue(r.recover.run()) // gathered, nothing left to do
        assertEquals(0, r.ads.inits)
        assertEquals(0, r.rc.refreshes)
        assertEquals(0, r.preloads)
    }

    @Test
    fun doesNothingBeforeStartupGatherFinished() = runTest {
        val r = Rig(online = true)
        r.tracker.startupGatherFinished = false
        r.tracker.consentGatherPending = true
        assertFalse(r.recover.needsRecovery())
        assertFalse(r.recover.run())
        assertEquals(0, r.consent.gathers)
    }

    @Test
    fun doesNothingWhileConsentFormShowing() = runTest {
        val r = Rig(online = true)
        r.tracker.consentGatherPending = true
        r.tracker.consentFormShowing = true
        assertFalse(r.recover.run())
        assertEquals(0, r.consent.gathers)
    }

    @Test
    fun secondPassIsIdempotent_noDoubleGatherOrInit() = runTest {
        val r = Rig(online = false)
        r.consent.gatherConsent()
        r.net.flow.value = true
        r.recover.run()
        r.recover.run()
        assertEquals(2, r.consent.gathers) // startup + one recovery
        assertEquals(1, r.ads.inits)
    }

    @Test
    fun failedInit_isRetriedByBoundedRetries() = runTest {
        val r = Rig(online = false, failFirstInit = true)
        r.consent.gatherConsent()
        r.net.flow.value = true
        r.recover.runWithRetries(listOf(1_000L, 2_000L))
        assertEquals(2, r.ads.inits)
        assertTrue(r.ads.isSdkInitialized)
    }

    @Test
    fun retriesStopWhenOfflineAgain() = runTest {
        val r = Rig(online = false, failFirstInit = true)
        r.consent.gatherConsent()
        r.net.flow.value = true
        val job = launch { r.recover.runWithRetries(listOf(1_000L, 2_000L)) }
        runCurrent() // first pass ran, init failed, now waiting out the first back-off
        r.net.flow.value = false
        advanceUntilIdle()
        assertTrue(job.isCompleted)
        assertEquals(1, r.ads.inits)
    }
}
