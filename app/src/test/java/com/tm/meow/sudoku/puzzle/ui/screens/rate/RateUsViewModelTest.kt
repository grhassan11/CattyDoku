package com.tm.meow.sudoku.puzzle.ui.screens.rate

import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import com.tm.meow.sudoku.puzzle.domain.usecase.DismissRateUs
import com.tm.meow.sudoku.puzzle.domain.usecase.FakeProgressRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.FakeTimeProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.SubmitRateUs
import com.tm.meow.sudoku.puzzle.domain.usecase.TryShowRateUs
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeAppFlagsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RateUsViewModelTest {
    private val flags = FakeAppFlagsRepository(AppFlags())

    private fun vm() = RateUsViewModel(
        TryShowRateUs(FakeProgressRepository(), flags, AdSessionTracker(FakeTimeProvider(now = 1L))),
        DismissRateUs(flags),
        SubmitRateUs(flags),
    )

    private inline fun withMain(crossinline block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun lowStarClosesAndEmitsFeedbackEmail() = withMain {
        val vm = vm()
        vm.showManually()
        vm.onSubmit(2)
        assertFalse(vm.visible.value)
        assertEquals(RateUsEvent.OpenFeedbackEmail(2), vm.events.first())
        assertFalse(flags.flags.first().rateUsRated)
    }

    @Test
    fun highStarClosesEmitsStoreAndMarksRated() = withMain {
        val vm = vm()
        vm.showManually()
        vm.onSubmit(5)
        assertFalse(vm.visible.value)
        assertEquals(RateUsEvent.OpenStoreListing, vm.events.first())
        assertTrue(flags.flags.first().rateUsRated)
    }

    @Test
    fun exitSheetLowStarEmitsEmailWithoutTouchingFlags() = withMain {
        val vm = vm()
        vm.onExitSheetStar(3)
        assertEquals(RateUsEvent.OpenFeedbackEmail(3), vm.events.first())
        val f = flags.flags.first()
        assertFalse(f.rateUsRated)
        assertFalse(f.rateUsShown)
        assertEquals(0, f.rateUsDismissedCount)
    }

    @Test
    fun exitSheetHighStarMarksRatedAndOpensStoreAgainOnRepeat() = withMain {
        val vm = vm()
        vm.onExitSheetStar(5)
        assertEquals(RateUsEvent.OpenStoreListing, vm.events.first())
        assertTrue(flags.flags.first().rateUsRated)
        vm.onExitSheetStar(4)
        assertEquals(RateUsEvent.OpenStoreListing, vm.events.first())
    }

    @Test
    fun manualCloseDoesNotBumpDismissedCounter() = withMain {
        val vm = vm()
        vm.showManually()
        vm.onDismiss()
        assertFalse(vm.visible.value)
        assertEquals(0, flags.flags.first().rateUsDismissedCount)
    }
}
