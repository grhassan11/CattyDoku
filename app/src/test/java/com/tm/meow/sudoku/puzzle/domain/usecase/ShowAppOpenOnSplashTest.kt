package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.AdShowResult
import com.tm.meow.sudoku.puzzle.domain.ads.AdsSdkStatus
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenAdRepository
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenConfig
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenContext
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenDecision
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenPolicy
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenSkipReason
import com.tm.meow.sudoku.puzzle.domain.config.AppRemoteConfig
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentState
import com.tm.meow.sudoku.puzzle.domain.startup.SplashTiming
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake app-open repo shared with SplashViewModelTest. */
class FakeAppOpenRepository(
    var ageMs: Long? = null,
    var loading: Boolean = false,
    var showBlock: suspend () -> AdShowResult = { AdShowResult.SHOWN },
    private val onPreload: (FakeAppOpenRepository) -> Unit = {},
) : AppOpenAdRepository {
    var preloads = 0
    var shows = 0
    override fun preload() {
        preloads++
        onPreload(this)
    }

    override fun isLoading() = loading
    override fun readyAdAgeMs() = ageMs
    override fun discard() {
        ageMs = null
    }

    override suspend fun show(): AdShowResult {
        shows++
        val r = showBlock()
        ageMs = null
        return r
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShowAppOpenOnSplashTest {

    private class Consent(allow: Boolean = true) : ConsentRepository {
        override val state: StateFlow<ConsentState> = MutableStateFlow(ConsentState(canRequestAds = allow))
        override suspend fun gatherConsent() = Unit
        override suspend fun showPrivacyOptions() = Unit
    }

    private class Sdk(ready: Boolean = true) : AdsSdkStatus {
        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(ready)
    }

    private fun build(
        repo: FakeAppOpenRepository,
        tracker: AdSessionTracker,
        allow: Boolean = true,
        ready: Boolean = true,
        splashEnabled: Boolean = true,
    ): ShowAppOpenOnSplash {
        val consent = Consent(allow)
        val sdk = Sdk(ready)
        val preload = PreloadAppOpenAd(consent, sdk, repo)
        val show = ShowAppOpenIfAllowed(consent, sdk, repo, tracker, AppOpenConfig(), preload)
        val rc = FakeRemoteConfigRepository(AppRemoteConfig(splashAppOpenAdShow = splashEnabled))
        return ShowAppOpenOnSplash(repo, preload, show, remoteConfig = rc)
    }

    @Test
    fun remoteConfigOffSkipsEverythingImmediately() = runTest {
        val repo = FakeAppOpenRepository(ageMs = 0L, loading = true)
        val t = tracker()
        val uc = build(repo, t, splashEnabled = false)
        assertFalse(uc.isEnabled())
        assertFalse(uc())
        assertEquals(0L, currentTime)
        assertEquals(0, repo.shows)
        assertEquals(0, repo.preloads)
        assertEquals(null, t.lastAdShownMs)
    }

    @Test
    fun remoteConfigOnShowsAsBefore() = runTest {
        val repo = FakeAppOpenRepository(ageMs = 0L)
        val uc = build(repo, tracker(), splashEnabled = true)
        assertTrue(uc.isEnabled())
        assertTrue(uc())
        assertEquals(1, repo.shows)
    }

    private fun tracker() = AdSessionTracker(FakeTimeProvider(today = 20_000L))

    @Test
    fun readyAdShowsAndRecordsLastShown() = runTest {
        val repo = FakeAppOpenRepository(ageMs = 0L)
        val t = tracker()
        assertTrue(build(repo, t)())
        assertEquals(1, repo.shows)
        assertTrue(t.lastAdShownMs != null)
        assertFalse(t.fullScreenAdShowing)
    }

    @Test
    fun loadingAdIsAwaitedThenShown() = runTest {
        val repo = FakeAppOpenRepository(ageMs = null, loading = true)
        val uc = build(repo, tracker())
        val finisher = launch {
            delay(1_000)
            repo.ageMs = 0L
            repo.loading = false
        }
        assertTrue(uc())
        finisher.join()
        assertEquals(1_000L, currentTime)
    }

    @Test
    fun loadNeverFinishingGivesUpAtTimeoutAndSkips() = runTest {
        val repo = FakeAppOpenRepository(ageMs = null, loading = true)
        assertFalse(build(repo, tracker())())
        assertEquals(ShowAppOpenOnSplash.DEFAULT_LOAD_WAIT_MS, currentTime)
        assertEquals(0, repo.shows)
    }

    @Test
    fun explicitWaitOverridesDefault() = runTest {
        val repo = FakeAppOpenRepository(ageMs = null, loading = true)
        assertFalse(build(repo, tracker())(SplashTiming.APP_OPEN_WAIT_FIRST_MS))
        assertEquals(SplashTiming.APP_OPEN_WAIT_FIRST_MS, currentTime)
        assertEquals(0, repo.shows)
    }

    @Test
    fun explicitLongWaitStillExitsAtOnceWhenAdReadyOrLoadFailed() = runTest {
        val ready = FakeAppOpenRepository(ageMs = 0L, loading = false)
        assertTrue(build(ready, tracker())(SplashTiming.APP_OPEN_WAIT_FIRST_MS))
        assertEquals(0L, currentTime)
        val failed = FakeAppOpenRepository(ageMs = null, loading = false)
        assertFalse(build(failed, tracker())(SplashTiming.APP_OPEN_WAIT_FIRST_MS))
        assertEquals(0L, currentTime)
    }

    @Test
    fun failedLoadIsRetriedUntilAdArrivesWithinBudget() = runTest {
        // load 1 is in flight, fails at 1 s; the retry (a new load) fills at 4 s.
        val repo = FakeAppOpenRepository(ageMs = null, loading = true, onPreload = { it.loading = true })
        val uc = build(repo, tracker())
        val driver = launch {
            delay(1_000)
            repo.loading = false // load 1 failed
            delay(3_000)
            repo.ageMs = 0L // retry loaded
            repo.loading = false
        }
        assertTrue(uc(SplashTiming.APP_OPEN_WAIT_FIRST_MS))
        driver.join()
        assertEquals(1, repo.shows)
        assertTrue("retried at least once", repo.preloads >= 2)
    }

    @Test
    fun failedLoadProceedsImmediately() = runTest {
        val repo = FakeAppOpenRepository(ageMs = null, loading = false)
        assertFalse(build(repo, tracker())())
        assertEquals(0L, currentTime)
    }

    @Test
    fun noConsentOrSdkNotReadySkips() = runTest {
        assertFalse(build(FakeAppOpenRepository(ageMs = 0L), tracker(), allow = false)())
        assertFalse(build(FakeAppOpenRepository(ageMs = 0L), tracker(), ready = false)())
    }

    @Test
    fun showFailureReturnsFalseAndClearsFlag() = runTest {
        val repo = FakeAppOpenRepository(ageMs = 0L, showBlock = { AdShowResult.FAILED })
        val t = tracker()
        assertFalse(build(repo, t)())
        assertFalse(t.fullScreenAdShowing)
        assertEquals(null, t.lastAdShownMs)
    }

    @Test
    fun dismissNeverReportedIsCutAtGuard() = runTest {
        val repo = FakeAppOpenRepository(ageMs = 0L, showBlock = { awaitCancellation() })
        val t = tracker()
        assertFalse(build(repo, t)())
        assertEquals(ShowAppOpenOnSplash.SHOW_GUARD_MS, currentTime)
        assertFalse(t.fullScreenAdShowing)
    }

    @Test
    fun otherFullScreenAdShowingBlocksSplashAd() = runTest {
        val repo = FakeAppOpenRepository(ageMs = 0L)
        val t = tracker().also { it.fullScreenAdShowing = true }
        assertFalse(build(repo, t)())
        assertEquals(0, repo.shows)
    }

    // ---- policy ----

    private fun ctx(coldStart: Boolean, fromSplash: Boolean) = AppOpenContext(
        coldStart = coldStart, fromSplash = fromSplash, canRequestAds = true, sdkInitialized = true,
        fullScreenAdShowing = false, consentFormShowing = false, adAgeMs = 0L,
    )

    @Test
    fun coldStartStillSkipsOnLifecyclePath() {
        assertEquals(AppOpenDecision.Skip(AppOpenSkipReason.COLD_START), AppOpenPolicy.decide(ctx(true, false)))
    }

    @Test
    fun splashLiftsColdStartSkip() {
        assertEquals(AppOpenDecision.Show, AppOpenPolicy.decide(ctx(true, true)))
    }

    @Test
    fun splashDoesNotLiftOtherGates() {
        assertEquals(
            AppOpenDecision.Skip(AppOpenSkipReason.ADS_REMOVED),
            AppOpenPolicy.decide(ctx(true, true).copy(adsRemoved = true)),
        )
        assertEquals(
            AppOpenDecision.Skip(AppOpenSkipReason.OFFLINE),
            AppOpenPolicy.decide(ctx(true, true).copy(online = false)),
        )
        assertEquals(
            AppOpenDecision.Skip(AppOpenSkipReason.AD_EXPIRED),
            AppOpenPolicy.decide(ctx(true, true).copy(adAgeMs = AppOpenConfig.DEFAULT_AD_EXPIRY_MS)),
        )
    }

    @Test
    fun adClickReturnSkipsPolicyOnce() {
        assertEquals(
            AppOpenDecision.Skip(AppOpenSkipReason.AD_CLICK_RETURN),
            AppOpenPolicy.decide(ctx(false, false).copy(returnFromAdClick = true)),
        )
    }

    @Test
    fun suppressedScreenSkipsPolicy() {
        assertEquals(
            AppOpenDecision.Skip(AppOpenSkipReason.SUPPRESSED_SCREEN),
            AppOpenPolicy.decide(ctx(false, false).copy(suppressedScreen = true)),
        )
    }

    @Test
    fun adClickReturnSkipsOneForegroundThenShowsAgain() = runTest {
        val repo = FakeAppOpenRepository(ageMs = 0L)
        val t = tracker()
        val consent = Consent(true)
        val sdk = Sdk(true)
        val preload = PreloadAppOpenAd(consent, sdk, repo)
        val show = ShowAppOpenIfAllowed(consent, sdk, repo, t, AppOpenConfig(), preload)
        t.markAdClicked()
        assertFalse(show(coldStart = false))
        assertEquals(0, repo.shows)
        assertTrue(show(coldStart = false))
        assertEquals(1, repo.shows)
    }
}
