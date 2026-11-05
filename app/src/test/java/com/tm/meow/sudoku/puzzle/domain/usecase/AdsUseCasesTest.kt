package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicyConfig
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.AdShowResult
import com.tm.meow.sudoku.puzzle.domain.ads.AdTrigger
import com.tm.meow.sudoku.puzzle.domain.ads.AdsRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentState
import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeAppFlagsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shared call log so tests can assert ORDER across fakes. */
private class CallLog {
    val calls = mutableListOf<String>()
}

private class FakeConsent(
    private val log: CallLog,
    /** What state becomes after gatherConsent(). */
    private val afterGather: ConsentState,
    private val gatherThrows: Boolean = false,
) : ConsentRepository {
    private val _state = MutableStateFlow(ConsentState())
    override val state: StateFlow<ConsentState> = _state
    var privacyShown = 0
    var afterPrivacy: ConsentState? = null

    fun set(s: ConsentState) {
        _state.value = s
    }

    override suspend fun gatherConsent() {
        log.calls += "consent"
        if (gatherThrows) error("boom")
        _state.value = afterGather
    }

    override suspend fun showPrivacyOptions() {
        privacyShown++
        afterPrivacy?.let { _state.value = it }
    }
}

private class FakeAds(
    private val log: CallLog,
    var result: AdShowResult = AdShowResult.SHOWN,
    private val initThrows: Boolean = false,
) : AdsRepository {
    var initCalls = 0
    var showCalls = 0
    var lastWait = -1L

    override suspend fun initialize() {
        log.calls += "init"
        initCalls++
        if (initThrows) error("boom")
    }

    override suspend fun showIfReady(loadWaitTimeoutMs: Long): AdShowResult {
        showCalls++
        lastWait = loadWaitTimeoutMs
        return result
    }
}

class AdsUseCasesTest {

    private val allow = ConsentState(canRequestAds = true, privacyOptionsRequired = true)
    private val deny = ConsentState(canRequestAds = false, privacyOptionsRequired = false)

    // ---------------------------------------------------------------- consent -> init order

    @Test
    fun consentIsGatheredBeforeAdsInitialise() = runTest {
        val log = CallLog()
        val flags = FakeAppFlagsRepository()
        GatherConsentAndInitAds(FakeConsent(log, allow), FakeAds(log), flags)()
        assertEquals(listOf("consent", "init"), log.calls)
    }

    @Test
    fun noConsentMeansAdsNeverInitialise() = runTest {
        val log = CallLog()
        val ads = FakeAds(log)
        GatherConsentAndInitAds(FakeConsent(log, deny), ads, FakeAppFlagsRepository())()
        assertEquals(listOf("consent"), log.calls)
        assertEquals(0, ads.initCalls)
    }

    @Test
    fun consentErrorFailsOpenAndKeepsAdsOff() = runTest {
        val log = CallLog()
        val ads = FakeAds(log)
        // gather throws: use case returns normally, state stayed at the fail-closed default
        GatherConsentAndInitAds(FakeConsent(log, allow, gatherThrows = true), ads, FakeAppFlagsRepository())()
        assertEquals(0, ads.initCalls)
    }

    @Test
    fun adInitErrorDoesNotPropagate() = runTest {
        val log = CallLog()
        GatherConsentAndInitAds(FakeConsent(log, allow), FakeAds(log, initThrows = true), FakeAppFlagsRepository())()
        assertEquals(listOf("consent", "init"), log.calls)
    }

    @Test
    fun sessionAdCountResetsOnEveryStart() = runTest {
        val log = CallLog()
        val flags = FakeAppFlagsRepository(AppFlags(sessionAdCount = 5, lastInterstitialLevel = 12))
        GatherConsentAndInitAds(FakeConsent(log, deny), FakeAds(log), flags)()
        val f = flags.flags.first()
        assertEquals(0, f.sessionAdCount)
        assertEquals("lastInterstitialLevel is persisted across sessions", 12, f.lastInterstitialLevel)
    }

    // ---------------------------------------------------------------- restored-back-stack fallback (A-016)

    private class CountingRemoteConfig : com.tm.meow.sudoku.puzzle.domain.config.RemoteConfigRepository {
        var refreshes = 0
        override fun current() = com.tm.meow.sudoku.puzzle.domain.config.AppRemoteConfig()
        override fun refresh() {
            refreshes++
        }
    }

    @Test
    fun fallbackRunsConsentThenInitThenConfigFetchOnce() = runTest {
        val log = CallLog()
        val tracker = AdSessionTracker(FakeTimeProvider())
        val rc = CountingRemoteConfig()
        val gather = GatherConsentAndInitAds(FakeConsent(log, allow), FakeAds(log), FakeAppFlagsRepository(), tracker)
        val ensure = EnsureConsentAndAdsInit(gather, RefreshRemoteConfig(rc), tracker)
        assertTrue(ensure())
        assertEquals(listOf("consent", "init"), log.calls)
        assertEquals(1, rc.refreshes)
        assertFalse("second call is a no-op", ensure())
        assertEquals(listOf("consent", "init"), log.calls)
    }

    @Test
    fun fallbackSkipsWhenSplashAlreadyStartedTheGather() = runTest {
        val log = CallLog()
        val tracker = AdSessionTracker(FakeTimeProvider())
        val gather = GatherConsentAndInitAds(FakeConsent(log, allow), FakeAds(log), FakeAppFlagsRepository(), tracker)
        gather() // what the splash does
        assertTrue(tracker.startupGatherStarted)
        log.calls.clear()
        val ensure = EnsureConsentAndAdsInit(gather, RefreshRemoteConfig(CountingRemoteConfig()), tracker)
        assertFalse(ensure())
        assertTrue(log.calls.isEmpty())
    }

    @Test
    fun fallbackFailsOpen() = runTest {
        val log = CallLog()
        val tracker = AdSessionTracker(FakeTimeProvider())
        val gather = GatherConsentAndInitAds(
            FakeConsent(log, allow, gatherThrows = true), FakeAds(log), FakeAppFlagsRepository(), tracker,
        )
        assertTrue(EnsureConsentAndAdsInit(gather, RefreshRemoteConfig(CountingRemoteConfig()), tracker)())
    }

    // ---------------------------------------------------------------- privacy options

    @Test
    fun privacyOptionsInitialisesAdsWhenItGrantsConsent() = runTest {
        val log = CallLog()
        val consent = FakeConsent(log, deny).apply { afterPrivacy = allow }
        val ads = FakeAds(log)
        ShowPrivacyOptions(consent, ads)()
        assertEquals(1, consent.privacyShown)
        assertEquals(1, ads.initCalls)
    }

    @Test
    fun privacyOptionsWithoutConsentDoesNotInitialise() = runTest {
        val log = CallLog()
        val consent = FakeConsent(log, deny)
        val ads = FakeAds(log)
        ShowPrivacyOptions(consent, ads)()
        assertEquals(0, ads.initCalls)
    }

    @Test
    fun observeConsentStateExposesRepoState() = runTest {
        val consent = FakeConsent(CallLog(), allow)
        consent.set(allow)
        assertTrue(ObserveConsentState(consent)().first().privacyOptionsRequired)
    }

    // ---------------------------------------------------------------- show use case

    private class Rig(
        consentState: ConsentState = ConsentState(canRequestAds = true),
        adResult: AdShowResult = AdShowResult.SHOWN,
        initialFlags: AppFlags = AppFlags(),
        config: AdPolicyConfig = AdPolicyConfig(),
    ) {
        // Session starts at t=1_000_000 (tracker built now); tests advance the shared clock afterwards.
        val time = FakeTimeProvider(now = 1_000_000L)
        val tracker = AdSessionTracker(time)
        val log = CallLog()
        val consent = FakeConsent(log, consentState).also { it.set(consentState) }
        val ads = FakeAds(log, adResult)
        val flags = FakeAppFlagsRepository(initialFlags)
        val show = ShowInterstitialIfAllowed(consent, ads, tracker, flags, config)
    }

    /** Rig whose session is old enough (clock moved past the min session age after tracker creation). */
    private fun rig(
        consentState: ConsentState = ConsentState(canRequestAds = true),
        adResult: AdShowResult = AdShowResult.SHOWN,
        flags: AppFlags = AppFlags(),
        config: AdPolicyConfig = AdPolicyConfig(),
        wins: Int = 2,
        advanceMs: Long = 200_000L,
    ): Rig = Rig(consentState, adResult, flags, config).also {
        repeat(wins) { _ -> it.tracker.recordWin() }
        it.time.now += advanceMs
    }

    @Test
    fun showsWhenPolicyAllowsAndRecordsEverything() = runTest {
        val r = rig()
        assertTrue(r.show(AdTrigger.LEVEL_WON_CONTINUE, level = 20))
        assertEquals(1, r.ads.showCalls)
        assertEquals(AdPolicyConfig.DEFAULT_LOAD_WAIT_TIMEOUT_MS, r.ads.lastWait)
        assertEquals(0, r.tracker.winsSinceLastAd)
        assertEquals(1, r.tracker.adsShown)
        assertEquals(r.time.now, r.tracker.lastAdShownMs)
        val f = r.flags.flags.first()
        assertEquals(20, f.lastInterstitialLevel)
        assertEquals(1, f.sessionAdCount)
    }

    @Test
    fun policyDenialNeverTouchesTheSdk() = runTest {
        val r = rig(wins = 1, flags = AppFlags(lastInterstitialLevel = 12)) // level 16 is not a multiple of the default frequency 5
        assertFalse(r.show(AdTrigger.LEVEL_WON_CONTINUE, level = 16))
        assertEquals(0, r.ads.showCalls)
    }

    @Test
    fun loseRetryShowsByDefaultButDoesNotMarkTheLevelAsHavingHadItsAd() = runTest {
        val r = rig()
        assertTrue(r.show(AdTrigger.LOSE_RETRY, level = 11)) // frequency does not apply to Retry
        assertEquals(1, r.ads.showCalls)
        val f = r.flags.flags.first()
        assertEquals(0, f.lastInterstitialLevel)
        assertEquals(1, f.sessionAdCount)
    }

    @Test
    fun loseRetrySwitchOffNeverTouchesTheSdk() = runTest {
        val r = rig(config = AdPolicyConfig(levelsRestartInterstitialShow = false))
        assertFalse(r.show(AdTrigger.LOSE_RETRY, level = 20))
        assertEquals(0, r.ads.showCalls)
    }

    @Test
    fun noConsentNeverTouchesTheSdk() = runTest {
        val r = rig(consentState = ConsentState(canRequestAds = false))
        assertFalse(r.show(AdTrigger.LEVEL_WON_CONTINUE, level = 20))
        assertEquals(0, r.ads.showCalls)
    }

    @Test
    fun dailyNeverShows() = runTest {
        val r = rig()
        assertFalse(r.show(AdTrigger.LEVEL_WON_CONTINUE, level = 20, isDaily = true))
        assertEquals(0, r.ads.showCalls)
    }

    @Test
    fun launchResumeMidGameDailyStartNeverShow() = runTest {
        val r = rig()
        for (t in listOf(AdTrigger.APP_LAUNCH, AdTrigger.APP_RESUME, AdTrigger.MID_GAMEPLAY, AdTrigger.DAILY_START, AdTrigger.TUTORIAL)) {
            assertFalse("$t", r.show(t, level = 20))
        }
        assertEquals(0, r.ads.showCalls)
    }

    @Test
    fun notReadyOrFailedDoesNotRecordAndKeepsWinCount() = runTest {
        for (res in listOf(AdShowResult.NOT_READY, AdShowResult.FAILED)) {
            val r = rig(adResult = res)
            assertFalse("$res", r.show(AdTrigger.LEVEL_WON_CONTINUE, level = 20))
            assertEquals(1, r.ads.showCalls)
            assertEquals(2, r.tracker.winsSinceLastAd)
            assertEquals(0, r.tracker.adsShown)
            assertEquals(0, r.flags.flags.first().lastInterstitialLevel)
        }
    }

    @Test
    fun secondAdBlockedByMinIntervalThenAllowedAfterIt() = runTest {
        val r = rig()
        assertTrue(r.show(AdTrigger.LEVEL_WON_CONTINUE, level = 20))
        r.tracker.recordWin(); r.tracker.recordWin()
        r.time.now += 10_000L
        assertFalse(r.show(AdTrigger.LEVEL_WON_CONTINUE, level = 25))
        r.time.now += 90_000L
        assertTrue(r.show(AdTrigger.LEVEL_WON_CONTINUE, level = 25))
        assertEquals(2, r.flags.flags.first().sessionAdCount)
    }

    @Test
    fun youngSessionBlocksAd() = runTest {
        val r = rig(advanceMs = 30_000L)
        assertFalse(r.show(AdTrigger.LEVEL_WON_CONTINUE, level = 20))
        assertEquals(0, r.ads.showCalls)
    }

    @Test
    fun repoThrowingFailsOpen() = runTest {
        val r = rig()
        val throwing = object : AdsRepository {
            override suspend fun initialize() = Unit
            override suspend fun showIfReady(loadWaitTimeoutMs: Long): AdShowResult = error("sdk boom")
        }
        val uc = ShowInterstitialIfAllowed(r.consent, throwing, r.tracker, r.flags, AdPolicyConfig())
        assertFalse(uc(AdTrigger.LEVEL_WON_CONTINUE, level = 20))
    }

    @Test
    fun recordLevelWonIgnoresDaily() {
        val tracker = AdSessionTracker(FakeTimeProvider())
        val record = RecordLevelWon(tracker)
        record(isDaily = true)
        assertEquals(0, tracker.winsSinceLastAd)
        record(isDaily = false)
        record(isDaily = false)
        assertEquals(2, tracker.winsSinceLastAd)
    }

    @Test
    fun adFreeLevelsNeverReachTheSdkEvenWhenEverythingElseAllows() = runTest {
        for (level in 1..9) {
            val r = rig()
            assertFalse("L$level", r.show(AdTrigger.LEVEL_WON_CONTINUE, level = level))
            assertEquals("L$level", 0, r.ads.showCalls)
        }
        val r = rig()
        assertTrue(r.show(AdTrigger.LEVEL_WON_CONTINUE, level = 10))
        assertEquals(1, r.ads.showCalls)
    }
}
