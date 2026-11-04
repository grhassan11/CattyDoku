package com.tm.meow.sudoku.puzzle.ui.screens.tutorial

import app.cash.turbine.test
import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import com.tm.meow.sudoku.puzzle.domain.model.Pos
import com.tm.meow.sudoku.puzzle.domain.model.Settings
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialEngine
import com.tm.meow.sudoku.puzzle.domain.usecase.CompleteTutorial
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveSettings
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeAppFlagsRepository
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeSettingsRepository
import com.tm.meow.sudoku.puzzle.ui.screens.game.HapticKind
import com.tm.meow.sudoku.puzzle.ui.screens.game.SfxKind
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
        onCellTap(Pos(0, 2)); onCellDoubleTap(Pos(0, 2)) // 0 place
        onContinue() // 1 recap
        for (p in listOf(Pos(0, 0), Pos(0, 1), Pos(0, 3), Pos(1, 2), Pos(2, 2), Pos(3, 2))) onCellTap(p) // 2 row + col
        onCellTap(Pos(3, 1)); onCellDoubleTap(Pos(3, 1)) // 3 rose
        onDrag(listOf(Pos(2, 1), Pos(2, 0), Pos(3, 0))) // 4 swipe
        onCellTap(Pos(1, 0)); onCellDoubleTap(Pos(1, 0)) // 5 blue
        onCellTap(Pos(1, 1)); onCellTap(Pos(1, 3)) // 6 row done
        onCellTap(Pos(3, 3)) // 7 row done
        onHintPill() // 8 last cat
        onContinue() // 9 Start Game
    }

    private fun TutorialViewModel.playTo(step: Int) {
        val moves = listOf<() -> Unit>(
            { onCellTap(Pos(0, 2)); onCellDoubleTap(Pos(0, 2)) },
            { onContinue() },
            { for (p in listOf(Pos(0, 0), Pos(0, 1), Pos(0, 3), Pos(1, 2), Pos(2, 2), Pos(3, 2))) onCellTap(p) },
            { onCellTap(Pos(3, 1)); onCellDoubleTap(Pos(3, 1)) },
            { onDrag(listOf(Pos(2, 1), Pos(2, 0), Pos(3, 0))) },
            { onCellTap(Pos(1, 0)); onCellDoubleTap(Pos(1, 0)) },
            { onCellTap(Pos(1, 1)); onCellTap(Pos(1, 3)) },
            { onCellTap(Pos(3, 3)) },
        )
        for (i in 0 until step) moves[i]()
    }

    @Test
    fun startsAtStepZeroWithEmptyBoard() = vmTest { _, vm ->
        val s = vm.state.value
        assertEquals(0, s.stepIndex)
        assertTrue(s.showSkip)
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
            vm.onCellDoubleTap(Pos(0, 0)) // cat on a non-solution cell
            assertEquals(TutorialUiEvent.Haptic(HapticKind.WRONG), awaitItem())
            assertEquals(TutorialUiEvent.Sfx(SfxKind.BLOCKED), awaitItem())
            assertEquals(2, vm.state.value.rejectCount)
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
            vm.onCellDoubleTap(Pos(0, 2)) // first tap of a double-tap pair is not required here; rejected or applied, both silent
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun correctCatMeowsAndPillMeowsToo() = vmTest { _, vm ->
        vm.events.test {
            vm.onCellTap(Pos(0, 2))
            vm.onCellDoubleTap(Pos(0, 2))
            assertEquals(TutorialUiEvent.Haptic(HapticKind.PLACE), awaitItem())
            assertEquals(TutorialUiEvent.Sfx(SfxKind.MEOW), awaitItem())
            assertTrue(cancelAndConsumeRemainingEvents().isEmpty())
        }
        vm.playTo(8)
        vm.events.test {
            vm.onHintPill()
            assertEquals(TutorialUiEvent.Haptic(HapticKind.PLACE), awaitItem())
            assertEquals(TutorialUiEvent.Sfx(SfxKind.MEOW), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun correctActionsAdvanceTheStep() = vmTest { _, vm ->
        vm.onCellTap(Pos(0, 2))
        assertEquals(0, vm.state.value.stepIndex)
        vm.onCellDoubleTap(Pos(0, 2))
        assertEquals(1, vm.state.value.stepIndex)
        vm.onContinue()
        assertEquals(2, vm.state.value.stepIndex)
    }

    @Test
    fun flagNotSetUntilTutorialEnds() = vmTest { rig, vm ->
        vm.playTo(2)
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
        vm.onCellDoubleTap(Pos(0, 2))
        assertEquals(before, vm.state.value)
    }

    @Test
    fun replayRestartsFromStepZeroWithAFreshBoard() = vmTest { rig, vm ->
        vm.playTo(8)
        assertEquals(8, vm.state.value.stepIndex)
        vm.onReplay()
        val s = vm.state.value
        assertEquals(0, s.stepIndex)
        assertTrue(s.board.cells.all { it.mark.name == "EMPTY" })
        assertFalse(rig.flags.flags.first().tutorialDone)
        vm.playAll() // still finishable after a replay
        assertEquals(AppFlags(tutorialDone = true), rig.flags.flags.first())
    }

    @Test
    fun pillAtStepEightFinishesTheStepWithoutTouchingAnyInventory() = vmTest { _, vm ->
        vm.playTo(8)
        vm.onHintPill()
        val s = vm.state.value
        assertEquals(9, s.stepIndex)
        assertTrue(s.continueIsFinal)
        assertEquals(4, s.board.cells.count { it.mark.name == "CAT" })
    }
}
