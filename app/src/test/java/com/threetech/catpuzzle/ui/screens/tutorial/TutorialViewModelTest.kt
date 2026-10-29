package com.threetech.catpuzzle.ui.screens.tutorial

import app.cash.turbine.test
import com.threetech.catpuzzle.domain.model.AppFlags
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.Settings
import com.threetech.catpuzzle.domain.tutorial.TutorialEngine
import com.threetech.catpuzzle.domain.tutorial.TutorialFocus
import com.threetech.catpuzzle.domain.usecase.CompleteTutorial
import com.threetech.catpuzzle.domain.usecase.ObserveSettings
import com.threetech.catpuzzle.ui.screens.game.FakeAppFlagsRepository
import com.threetech.catpuzzle.ui.screens.game.FakeSettingsRepository
import com.threetech.catpuzzle.ui.screens.game.HapticKind
import com.threetech.catpuzzle.ui.screens.game.SfxKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TutorialViewModelTest {

    private class Rig(settings: Settings = Settings()) {
        val flags = FakeAppFlagsRepository()
        val settingsRepo = FakeSettingsRepository(settings)

        fun viewModel() = TutorialViewModel(
            engine = TutorialEngine(),
            observeSettings = ObserveSettings(settingsRepo),
            completeTutorial = CompleteTutorial(flags),
        )
    }

    private fun vmTest(
        rig: Rig = Rig(),
        block: suspend TestScope.(Rig, TutorialViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            block(rig, rig.viewModel())
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** Correct run through every step, as the gesture layer would report it. */
    private fun TutorialViewModel.playAll() {
        onCellTap(Pos(0, 1)); onCellDoubleTap(Pos(0, 1)) // 0 place
        onContinue() // 1 info
        onCellTap(Pos(0, 0)); onCellTap(Pos(0, 2)); onCellTap(Pos(0, 3)) // 2 row
        onDrag(listOf(Pos(1, 0), Pos(1, 1), Pos(1, 2))) // 3 swipe
        onCellTap(Pos(1, 3)); onCellDoubleTap(Pos(1, 3)) // 4 place
        onContinue() // 5 hearts
        onHint() // 6 hint
        onCellTap(Pos(2, 0)); onCellDoubleTap(Pos(2, 0)) // 7 place
        onCatBooster() // 8 cat booster
        onContinue() // 9 done
    }

    @Test
    fun startsAtStepZeroWithEmptyBoard() = vmTest { _, vm ->
        val s = vm.state.value
        assertEquals(0, s.stepIndex)
        assertEquals(3, s.hearts)
        assertEquals(TutorialFocus.BOARD, s.focus)
        assertTrue(s.board.cells.all { it.mark.name == "EMPTY" })
    }

    @Test
    fun wrongActionShakesVibratesAndKeepsHeartsAndStep() = vmTest { _, vm ->
        vm.events.test {
            vm.onCellTap(Pos(3, 3))
            assertEquals(TutorialUiEvent.Haptic(HapticKind.WRONG), awaitItem())
            assertEquals(TutorialUiEvent.Sfx(SfxKind.BLOCKED), awaitItem())
            val s = vm.state.value
            assertEquals(1, s.rejectCount)
            assertEquals(0, s.stepIndex)
            assertEquals(3, s.hearts)
            vm.onCellDoubleTap(Pos(0, 0)) // cat on a non-solution cell
            assertEquals(TutorialUiEvent.Haptic(HapticKind.WRONG), awaitItem())
            assertEquals(TutorialUiEvent.Sfx(SfxKind.BLOCKED), awaitItem())
            assertEquals(2, vm.state.value.rejectCount)
            assertEquals(3, vm.state.value.hearts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun hapticsSettingOffSilencesHaptics() = vmTest(Rig(Settings(haptics = false))) { _, vm ->
        vm.events.test {
            vm.onCellTap(Pos(3, 3))
            assertEquals(TutorialUiEvent.Sfx(SfxKind.BLOCKED), awaitItem()) // sfx still on
            expectNoEvents()
            assertEquals(1, vm.state.value.rejectCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sfxSettingOffSilencesSfx() = vmTest(Rig(Settings(haptics = false, sfx = false))) { _, vm ->
        vm.events.test {
            vm.onCellTap(Pos(3, 3))
            vm.onCellDoubleTap(Pos(0, 1)) // first tap of a double-tap pair is not required here; rejected or applied, both silent
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun correctCatMeowsAndHintStepPlaysHint() = vmTest { _, vm ->
        vm.events.test {
            vm.onCellTap(Pos(0, 1))
            vm.onCellDoubleTap(Pos(0, 1))
            assertEquals(TutorialUiEvent.Haptic(HapticKind.PLACE), awaitItem())
            assertEquals(TutorialUiEvent.Sfx(SfxKind.MEOW), awaitItem())
            val rest = cancelAndConsumeRemainingEvents()
            assertTrue(rest.isEmpty())
        }
        vm.events.test {
            vm.onContinue()
            vm.onCellTap(Pos(0, 0)); vm.onCellTap(Pos(0, 2)); vm.onCellTap(Pos(0, 3))
            vm.onDrag(listOf(Pos(1, 0), Pos(1, 1), Pos(1, 2)))
            vm.onCellTap(Pos(1, 3)); vm.onCellDoubleTap(Pos(1, 3)); vm.onContinue()
            vm.onHint()
            val items = cancelAndConsumeRemainingEvents()
                .mapNotNull { (it as? app.cash.turbine.Event.Item<*>)?.value }
            assertTrue(items.contains(TutorialUiEvent.Sfx(SfxKind.HINT)))
        }
    }

    @Test
    fun correctActionsAdvanceTheStep() = vmTest { _, vm ->
        vm.onCellTap(Pos(0, 1))
        assertEquals(0, vm.state.value.stepIndex)
        vm.onCellDoubleTap(Pos(0, 1))
        assertEquals(1, vm.state.value.stepIndex)
        vm.onContinue()
        assertEquals(2, vm.state.value.stepIndex)
    }

    @Test
    fun flagNotSetUntilTutorialEnds() = vmTest { rig, vm ->
        vm.onCellTap(Pos(0, 1)); vm.onCellDoubleTap(Pos(0, 1)); vm.onContinue()
        assertFalse(rig.flags.flags.first().tutorialDone)
    }

    @Test
    fun finishingSetsFlagEmitsFinishedAndTouchesNothingElse() = vmTest { rig, vm ->
        vm.events.test {
            vm.playAll()
            var finished = false
            while (!finished) if (awaitItem() == TutorialUiEvent.Finished) finished = true
            assertEquals(AppFlags(tutorialDone = true), rig.flags.flags.first())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun skipSetsFlagAndFinishesOnceEvenIfPressedTwice() = vmTest { rig, vm ->
        vm.events.test {
            vm.onSkip()
            vm.onSkip()
            assertEquals(TutorialUiEvent.Finished, awaitItem())
            assertEquals(AppFlags(tutorialDone = true), rig.flags.flags.first())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun inputAfterSkipIsIgnored() = vmTest { _, vm ->
        vm.onSkip()
        val before = vm.state.value
        vm.onCellTap(Pos(3, 3))
        vm.onCellDoubleTap(Pos(0, 1))
        assertEquals(before, vm.state.value)
    }

    @Test
    fun boosterStepsUseTutorialChargesOnly() = vmTest { _, vm ->
        vm.onCellTap(Pos(0, 1)); vm.onCellDoubleTap(Pos(0, 1)); vm.onContinue()
        vm.onCellTap(Pos(0, 0)); vm.onCellTap(Pos(0, 2)); vm.onCellTap(Pos(0, 3))
        vm.onDrag(listOf(Pos(1, 0), Pos(1, 1), Pos(1, 2)))
        vm.onCellTap(Pos(1, 3)); vm.onCellDoubleTap(Pos(1, 3)); vm.onContinue()
        assertEquals(1, vm.state.value.hintCharges)
        vm.onHint()
        assertEquals(0, vm.state.value.hintCharges)
        assertEquals(1, vm.state.value.catCharges)
    }
}
