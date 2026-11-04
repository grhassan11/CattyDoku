package com.tm.meow.sudoku.puzzle.ui.screens.splash

import app.cash.turbine.test
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.AdShowResult
import com.tm.meow.sudoku.puzzle.domain.ads.AdsRepository
import com.tm.meow.sudoku.puzzle.domain.config.AppRemoteConfig
import com.tm.meow.sudoku.puzzle.domain.config.RemoteConfigRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentState
import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import com.tm.meow.sudoku.puzzle.domain.model.Puzzle
import com.tm.meow.sudoku.puzzle.domain.repository.PuzzleRepository
import com.tm.meow.sudoku.puzzle.domain.startup.SplashTiming
import com.tm.meow.sudoku.puzzle.domain.usecase.FakeProgressRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.FakePuzzleRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.FakeSavedGameRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.FakeTimeProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.GatherConsentAndInitAds
import com.tm.meow.sudoku.puzzle.domain.usecase.GetOrCreatePuzzle
import com.tm.meow.sudoku.puzzle.domain.usecase.IsConsentFormShowing
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveTermsAccepted
import com.tm.meow.sudoku.puzzle.domain.usecase.PrefetchLevel
import com.tm.meow.sudoku.puzzle.domain.usecase.RefreshRemoteConfig
import com.tm.meow.sudoku.puzzle.domain.usecase.ResolveStartLevel
import com.tm.meow.sudoku.puzzle.domain.usecase.StartLevel
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeAppFlagsRepository
import com.tm.meow.sudoku.puzzle.ui.screens.game.RecordingGenerator
import com.tm.meow.sudoku.puzzle.ui.screens.game.fixturePuzzle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {

    /** Cache that never answers: models warm-up work that outlasts the warm-up cap. */
    private object HangingPuzzleRepository : PuzzleRepository {
        override suspend fun get(levelIndex: Int): Puzzle? = awaitCancellation()
        override suspend fun putIfAbsent(puzzle: Puzzle): Boolean = awaitCancellation()
    }

    private enum class ConsentMode { OK, THROWS, HANGS }

    private class FakeConsent(private val mode: ConsentMode, canRequestAds: Boolean = true) : ConsentRepository {
        var gathers = 0
        override val state: StateFlow<ConsentState> = MutableStateFlow(ConsentState(canRequestAds = canRequestAds))
        override suspend fun gatherConsent() {
            gathers++
            when (mode) {
                ConsentMode.OK -> Unit
                ConsentMode.THROWS -> error("ump down")
                ConsentMode.HANGS -> awaitCancellation()
            }
        }

        override suspend fun showPrivacyOptions() = Unit
    }

    private class FakeRemoteConfig : RemoteConfigRepository {
        var refreshes = 0
        override fun current() = AppRemoteConfig()
        override fun refresh() {
            refreshes++
        }
    }

    private class FakeAds(private val throws: Boolean = false) : AdsRepository {
        var inits = 0
        override suspend fun initialize() {
            inits++
            if (throws) error("ads down")
        }

        override suspend fun showIfReady(loadWaitTimeoutMs: Long) = AdShowResult.NOT_READY
    }

    private class Rig(
        flags: AppFlags = AppFlags(),
        hangWarm: Boolean = false,
        consentMode: ConsentMode = ConsentMode.OK,
        adsThrow: Boolean = false,
        canRequestAds: Boolean = true,
    ) {
        val flags = FakeAppFlagsRepository(flags)
        val puzzles = FakePuzzleRepository().apply { rows[1] = fixturePuzzle(1) }
        val generator = RecordingGenerator()
        private val cache: PuzzleRepository = if (hangWarm) HangingPuzzleRepository else puzzles
        val consent = FakeConsent(consentMode, canRequestAds)
        val remoteConfig = FakeRemoteConfig()
        val ads = FakeAds(adsThrow)
        val tracker = AdSessionTracker(FakeTimeProvider(today = 20_000L))

        fun viewModel() = SplashViewModel(
            observeTermsAccepted = ObserveTermsAccepted(this.flags),
            resolveStartLevel = ResolveStartLevel(FakeSavedGameRepository(), this.flags, FakeProgressRepository()),
            prefetchLevel = PrefetchLevel(
                GetOrCreatePuzzle(cache, StartLevel(generator), dispatcher = Dispatchers.Unconfined),
            ),
            gather = GatherConsentAndInitAds(consent, ads, this.flags),
            isConsentFormShowing = IsConsentFormShowing(tracker),
            refreshRemoteConfig = RefreshRemoteConfig(remoteConfig) { consent.state.value.canRequestAds },
        )
    }

    private fun vmTest(rig: Rig = Rig(), block: suspend TestScope.(Rig, SplashViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = rig.viewModel()
            vm.start()
            block(rig, vm)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun firstLaunchHoldsForMinimumThenFinishes() = vmTest { rig, vm ->
        vm.events.test {
            advanceTimeBy(SplashTiming.MIN_FIRST_MS - 1)
            expectNoEvents()
            advanceTimeBy(SplashTiming.TICK_MS * 2)
            runCurrent()
            assertEquals(SplashEvent.Finished, awaitItem())
            assertEquals(1, rig.consent.gathers)
            assertEquals(1, rig.ads.inits) // ads init ran after consent allowed it
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun returningLaunchSkipsTheLongWait() = vmTest(Rig(AppFlags(termsAccepted = true, termsVersion = 1))) { _, vm ->
        vm.events.test {
            advanceTimeBy(SplashTiming.MIN_RETURNING_MS - 1)
            expectNoEvents()
            advanceTimeBy(SplashTiming.TICK_MS * 2)
            runCurrent()
            assertEquals(SplashEvent.Finished, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun hungConsentIsCutOffAtTheHardCap() = vmTest(Rig(consentMode = ConsentMode.HANGS)) { _, vm ->
        vm.events.test {
            advanceTimeBy(SplashTiming.MAX_MS - 1)
            expectNoEvents()
            advanceTimeBy(SplashTiming.TICK_MS * 2)
            runCurrent()
            assertEquals(SplashEvent.Finished, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun consentFailureStillContinuesWithAdsOff() = vmTest(Rig(consentMode = ConsentMode.THROWS)) { rig, vm ->
        vm.events.test {
            advanceTimeBy(SplashTiming.MIN_FIRST_MS + SplashTiming.TICK_MS * 2)
            runCurrent()
            assertEquals(SplashEvent.Finished, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // GatherConsentAndInitAds swallows the failure; consent state still allows ads here, so init is attempted.
        assertEquals(1, rig.consent.gathers)
    }

    @Test
    fun adsInitFailureDoesNotBlock() = vmTest(Rig(adsThrow = true)) { rig, vm ->
        vm.events.test {
            advanceTimeBy(SplashTiming.MIN_FIRST_MS + SplashTiming.TICK_MS * 2)
            runCurrent()
            assertEquals(SplashEvent.Finished, awaitItem())
            assertEquals(1, rig.ads.inits)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun umpFormOnScreenPausesTheHardCap() = vmTest(Rig(consentMode = ConsentMode.HANGS)) { rig, vm ->
        rig.tracker.consentFormShowing = true
        vm.events.test {
            advanceTimeBy(SplashTiming.MAX_MS * 3)
            expectNoEvents() // user is reading the form: the clock did not run
            rig.tracker.consentFormShowing = false
            advanceTimeBy(SplashTiming.MAX_MS + SplashTiming.TICK_MS * 2)
            runCurrent()
            assertEquals(SplashEvent.Finished, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun hungWarmUpIsAbandonedAfterItsCapOnceConsentIsDone() = vmTest(Rig(hangWarm = true)) { _, vm ->
        vm.events.test {
            advanceTimeBy(SplashTiming.WARM_CAP_MS - 1)
            expectNoEvents()
            advanceTimeBy(SplashTiming.TICK_MS * 2)
            runCurrent()
            assertEquals(SplashEvent.Finished, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun startIsIdempotentSoConsentRunsOnce() = vmTest { rig, vm ->
        vm.start()
        vm.start()
        vm.events.test {
            advanceTimeBy(SplashTiming.MIN_FIRST_MS + SplashTiming.TICK_MS * 2)
            runCurrent()
            assertEquals(SplashEvent.Finished, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, rig.consent.gathers)
    }

    @Test
    fun warmUpPrefetchesTheStartLevelWhenNotCached() = vmTest { rig, vm ->
        rig.puzzles.rows.clear() // nothing cached: warm-up must generate level 1 (fake generator fails, but is asked)
        vm.events.test {
            advanceTimeBy(SplashTiming.MIN_FIRST_MS + SplashTiming.TICK_MS * 2)
            runCurrent()
            assertEquals(SplashEvent.Finished, awaitItem())
            assertTrue(rig.generator.levels.contains(1))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun remoteConfigFetchWaitsForConsentThenRunsOnce() = vmTest { rig, vm ->
        vm.events.test {
            advanceTimeBy(SplashTiming.MIN_FIRST_MS + SplashTiming.TICK_MS * 2)
            runCurrent()
            assertEquals(SplashEvent.Finished, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, rig.remoteConfig.refreshes)
    }

    @Test
    fun remoteConfigFetchSkippedWhenConsentDenies() = vmTest(Rig(canRequestAds = false)) { rig, vm ->
        vm.events.test {
            advanceTimeBy(SplashTiming.MIN_FIRST_MS + SplashTiming.TICK_MS * 2)
            runCurrent()
            assertEquals(SplashEvent.Finished, awaitItem()) // splash still completes
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, rig.remoteConfig.refreshes)
    }

    @Test
    fun remoteConfigFetchNotStartedWhileConsentIsPending() = vmTest(Rig(consentMode = ConsentMode.HANGS)) { rig, vm ->
        vm.events.test {
            advanceTimeBy(SplashTiming.MAX_MS + SplashTiming.TICK_MS * 2)
            runCurrent()
            assertEquals(SplashEvent.Finished, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, rig.remoteConfig.refreshes)
    }
}
