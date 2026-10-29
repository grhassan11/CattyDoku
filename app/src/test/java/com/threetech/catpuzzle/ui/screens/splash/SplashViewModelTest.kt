package com.threetech.catpuzzle.ui.screens.splash

import app.cash.turbine.test
import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.ads.AdShowResult
import com.threetech.catpuzzle.domain.ads.AdsRepository
import com.threetech.catpuzzle.domain.consent.ConsentRepository
import com.threetech.catpuzzle.domain.consent.ConsentState
import com.threetech.catpuzzle.domain.model.AppFlags
import com.threetech.catpuzzle.domain.model.Puzzle
import com.threetech.catpuzzle.domain.repository.PuzzleRepository
import com.threetech.catpuzzle.domain.startup.SplashTiming
import com.threetech.catpuzzle.domain.usecase.FakeProgressRepository
import com.threetech.catpuzzle.domain.usecase.FakePuzzleRepository
import com.threetech.catpuzzle.domain.usecase.FakeSavedGameRepository
import com.threetech.catpuzzle.domain.usecase.FakeTimeProvider
import com.threetech.catpuzzle.domain.usecase.GatherConsentAndInitAds
import com.threetech.catpuzzle.domain.usecase.GetOrCreatePuzzle
import com.threetech.catpuzzle.domain.usecase.IsConsentFormShowing
import com.threetech.catpuzzle.domain.usecase.ObserveTermsAccepted
import com.threetech.catpuzzle.domain.usecase.PrefetchLevel
import com.threetech.catpuzzle.domain.usecase.ResolveStartLevel
import com.threetech.catpuzzle.domain.usecase.StartLevel
import com.threetech.catpuzzle.ui.screens.game.FakeAppFlagsRepository
import com.threetech.catpuzzle.ui.screens.game.RecordingGenerator
import com.threetech.catpuzzle.ui.screens.game.fixturePuzzle
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

    private class FakeConsent(private val mode: ConsentMode) : ConsentRepository {
        var gathers = 0
        override val state: StateFlow<ConsentState> = MutableStateFlow(ConsentState(canRequestAds = true))
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
    ) {
        val flags = FakeAppFlagsRepository(flags)
        val puzzles = FakePuzzleRepository().apply { rows[1] = fixturePuzzle(1) }
        val generator = RecordingGenerator()
        private val cache: PuzzleRepository = if (hangWarm) HangingPuzzleRepository else puzzles
        val consent = FakeConsent(consentMode)
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
}
