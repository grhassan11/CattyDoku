package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.tutorial

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuSize
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier
import com.tm.meow.sudoku.puzzle.domain.sudoku.tutorial.SudokuTutorialEngine
import com.tm.meow.sudoku.puzzle.domain.sudoku.tutorial.SudokuTutorialPhase
import com.tm.meow.sudoku.puzzle.domain.sudoku.tutorial.SudokuTutorialScript
import com.tm.meow.sudoku.puzzle.domain.sudoku.tutorial.SudokuTutorialState
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.CompleteSudokuTutorial
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.IsSudokuTutorialDone
import com.tm.meow.sudoku.puzzle.ui.navigation.Routes
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
class SudokuTutorialUiTest {

    private val sizes = SudokuSize.entries

    private fun started(size: SudokuSize) = SudokuTutorialEngine.start(SudokuTutorialEngine.initial(size))

    private fun SudokuTutorialState.play(steps: Int): SudokuTutorialState {
        var s = this
        repeat(steps) {
            val step = checkNotNull(s.currentStep)
            s = SudokuTutorialEngine.enterDigit(SudokuTutorialEngine.tapCell(s, step.cell), step.digit)
        }
        return s
    }

    @Test
    fun introShowsNoDimAndNoPointerForEverySize() {
        for (size in sizes) {
            val ui = SudokuTutorialUiMapper.map(SudokuTutorialEngine.initial(size))
            assertEquals(size, ui.size)
            assertEquals(size.cells, ui.cells.size)
            assertTrue(ui.cells.none { it.dim || it.mark || it.target })
            assertEquals(-1, ui.pointerCell)
            assertEquals(0, ui.enabledDigit)
            assertEquals(listOf(TutorialSegment.CURRENT, TutorialSegment.TODO, TutorialSegment.TODO), ui.segments)
        }
    }

    @Test
    fun themeTierIsTheLowestTierOfTheSize() {
        val expected = mapOf(
            SudokuSize.S4 to SudokuTier.EASY,
            SudokuSize.S6 to SudokuTier.MEDIUM,
            SudokuSize.S8 to SudokuTier.HARD,
            SudokuSize.S9 to SudokuTier.MASTER,
        )
        for (size in sizes) {
            assertEquals(expected[size], SudokuTutorialUiMapper.map(SudokuTutorialEngine.initial(size)).themeTier)
        }
    }

    @Test
    fun tapPhasePointsAtTheTargetAndDimsOutsideTheUnit() {
        for (size in sizes) {
            val s = started(size)
            val step = checkNotNull(s.currentStep)
            val ui = SudokuTutorialUiMapper.map(s)
            assertEquals(step.cell, ui.pointerCell)
            assertEquals(0, ui.enabledDigit)
            assertTrue(ui.cells[step.cell].target)
            assertFalse(ui.cells[step.cell].question)
            val unit = SudokuTutorialScript.unitCells(size, step).toSet()
            ui.cells.forEachIndexed { i, c -> assertEquals("$size cell $i", i !in unit, c.dim) }
        }
    }

    @Test
    fun digitPhaseEnablesOnlyTheStepDigitAndShowsTheQuestionMark() {
        for (size in sizes) {
            val s0 = started(size)
            val step = checkNotNull(s0.currentStep)
            val ui = SudokuTutorialUiMapper.map(SudokuTutorialEngine.tapCell(s0, step.cell))
            assertEquals(step.digit, ui.enabledDigit)
            assertEquals(-1, ui.pointerCell)
            assertTrue(ui.cells[step.cell].question)
        }
    }

    @Test
    fun segmentsFollowProgress() {
        for (size in sizes) {
            var s = started(size).play(1)
            assertEquals(
                listOf(TutorialSegment.DONE, TutorialSegment.CURRENT, TutorialSegment.TODO),
                SudokuTutorialUiMapper.map(s).segments,
            )
            s = s.play(2)
            assertEquals(SudokuTutorialPhase.LESSON_COMPLETE, s.phase)
            assertTrue(SudokuTutorialUiMapper.map(s).segments.all { it == TutorialSegment.DONE })
        }
    }

    @Test
    fun trickStepMarksBlockedCellsInTheUi() {
        // marks of the first trick step per size (hand-checked against the scripted boards)
        val expectedMarks = mapOf(SudokuSize.S4 to 2, SudokuSize.S6 to 2, SudokuSize.S8 to 4, SudokuSize.S9 to 3)
        for (size in sizes) {
            val l2 = SudokuTutorialEngine.start(checkNotNull(SudokuTutorialEngine.nextLesson(started(size).play(3))))
            val ui = SudokuTutorialUiMapper.map(l2)
            assertEquals(2, ui.lesson)
            assertEquals(2, ui.segments.size)
            assertEquals("$size", expectedMarks[size], ui.cells.count { it.mark })
            assertTrue(ui.cells.none { it.mark && it.target })
        }
    }

    @Test
    fun textArgsMatchTheStringsPlaceholders() {
        for (size in sizes) {
            var s = started(size)
            // rule 1 (row) and 2 (column): size only
            assertEquals(listOf(size.n), SudokuTutorialUiMapper.textArgs(s))
            s = s.play(2)
            // rule 3 (block): rows, cols, n
            assertEquals(listOf(size.box.rows, size.box.cols, size.n), SudokuTutorialUiMapper.textArgs(s))
            // digit phase: the digit
            val step = checkNotNull(s.currentStep)
            assertEquals(listOf(step.digit), SudokuTutorialUiMapper.textArgs(SudokuTutorialEngine.tapCell(s, step.cell)))
        }
    }

    // ---- flag + view model

    private fun vmTest(
        size: SudokuSize = SudokuSize.S9,
        flags: FakeAppFlagsRepository = FakeAppFlagsRepository(),
        block: suspend (FakeAppFlagsRepository, SudokuTutorialViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val handle = SavedStateHandle(mapOf(Routes.ARG_SUDOKU_TUTORIAL_SIZE to size.name))
            block(flags, SudokuTutorialViewModel(handle, CompleteSudokuTutorial(flags)))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun exitSavesOnlyThatSizesFlagAndEmitsFinishedOnce() = vmTest(SudokuSize.S6) { flags, vm ->
        vm.events.test {
            vm.onExit()
            vm.onExit()
            assertEquals(SudokuTutorialEvent.Finished, awaitItem())
            expectNoEvents()
        }
        assertEquals(AppFlags(sudokuTutorialSeen = setOf(SudokuSize.S6)), flags.flags.first())
    }

    @Test
    fun viewModelPlaysTheTutorialOfItsSize() = vmTest(SudokuSize.S4) { _, vm ->
        assertEquals(SudokuSize.S4, vm.state.value.size)
        assertEquals(SudokuSize.S4.cells, vm.state.value.cells.size)
    }

    @Test
    fun missingSizeArgFallsBackToNineByNine() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val vm = SudokuTutorialViewModel(SavedStateHandle(), CompleteSudokuTutorial(FakeAppFlagsRepository()))
            assertEquals(SudokuSize.S9, vm.state.value.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun playingTheTutorialWritesNothingUntilExit() = vmTest(SudokuSize.S8) { flags, vm ->
        vm.onStartLesson()
        var step = SudokuTutorialScript.lesson(SudokuSize.S8, 1).steps.first()
        vm.onCellTap(step.cell)
        vm.onDigit(step.digit)
        assertEquals(1, vm.state.value.solved)
        assertEquals(AppFlags(), flags.flags.first())
        step = SudokuTutorialScript.lesson(SudokuSize.S8, 1).steps[1]
        vm.onCellTap(step.cell)
        vm.onDigit(step.digit)
        assertEquals(2, vm.state.value.solved)
        assertEquals(AppFlags(), flags.flags.first())
    }

    @Test
    fun nextLessonWithNoNextLessonExits() = vmTest { flags, vm ->
        vm.events.test {
            vm.onNextLesson() // still on lesson 1 intro: no next lesson from here
            assertEquals(SudokuTutorialEvent.Finished, awaitItem())
        }
        assertTrue(SudokuSize.S9 in flags.flags.first().sudokuTutorialSeen)
    }

    @Test
    fun gateShowsPerSizeUntilThatSizesFlagIsSet() = vmTest { flags, _ ->
        val gate = SudokuTutorialGateViewModel(IsSudokuTutorialDone(flags))
        for (size in sizes) assertTrue("$size", gate.shouldShow(size))
        CompleteSudokuTutorial(flags).invoke(SudokuSize.S6)
        assertFalse(gate.shouldShow(SudokuSize.S6))
        assertTrue(gate.shouldShow(SudokuSize.S4))
        assertTrue(gate.shouldShow(SudokuSize.S8))
        assertTrue(gate.shouldShow(SudokuSize.S9))
        CompleteSudokuTutorial(flags).invoke(SudokuSize.S6) // idempotent
        assertEquals(setOf(SudokuSize.S6), flags.flags.first().sudokuTutorialSeen)
    }

    @Test
    fun sudokuFlagsAreSeparateFromTheQueensTutorialFlag() = vmTest { flags, vm ->
        vm.events.test {
            vm.onExit()
            awaitItem()
        }
        val f = flags.flags.first()
        assertTrue(SudokuSize.S9 in f.sudokuTutorialSeen)
        assertFalse(f.tutorialDone)
    }
}
