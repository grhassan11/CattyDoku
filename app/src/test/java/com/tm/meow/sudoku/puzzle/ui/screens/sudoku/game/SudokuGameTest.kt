package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game

import androidx.lifecycle.SavedStateHandle
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.analytics.RecordingAnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.model.Booster
import com.tm.meow.sudoku.puzzle.domain.sudoku.engine.SUDOKU_GENERATOR_VERSION
import com.tm.meow.sudoku.puzzle.domain.sudoku.engine.SudokuGameEngine
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.BoxShape
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuGameState
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuPuzzle
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuStatus
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTechnique
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.CompleteSudoku
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.FakeSudokuRepository
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.SaveSudokuGame
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.ShowSudokuInterstitialIfAllowed
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.StartOrResumeSudoku
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.UseSudokuHint
import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.ConsumeBooster
import com.tm.meow.sudoku.puzzle.domain.usecase.FakeInventoryRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.FakeStreakRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveBoosterBalances
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveSettings
import com.tm.meow.sudoku.puzzle.domain.usecase.RecordSudokuWon
import com.tm.meow.sudoku.puzzle.domain.usecase.RecordPlayedToday
import com.tm.meow.sudoku.puzzle.ui.navigation.Routes
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeSettingsRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SudokuGameTest {

    // 4x4 (EASY). Cells 0 and 15 are empty; both solve to 1.
    private val sol4 = intArrayOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 1, 4, 3, 4, 3, 2, 1)

    private fun newState(): SudokuGameState {
        val puzzle = SudokuPuzzle(
            SudokuTier.EASY, sol4.copyOf().also { it[0] = 0; it[15] = 0 }, sol4, 1L,
            SUDOKU_GENERATOR_VERSION, SudokuTechnique.NAKED_SINGLE, true,
        )
        return SudokuGameEngine.newGame(puzzle)
    }

    // ---- mapper ----

    @Test
    fun highlightsSelectedPeersAndSameDigit() {
        val g = SudokuGameEngine.select(newState(), 1) // value 2
        val cells = SudokuGameUiMapper.cells(g)
        assertEquals(CellHighlight.SELECTED, cells[1].highlight)
        assertEquals(CellHighlight.PEER, cells[5].highlight) // same column + box, other digit
        assertEquals(CellHighlight.SAME_DIGIT, cells[7].highlight)
        assertEquals(CellHighlight.SAME_DIGIT, cells[8].highlight)
        assertEquals(CellHighlight.SAME_DIGIT, cells[14].highlight)
        assertEquals(CellHighlight.NONE, cells[10].highlight)
        assertTrue(cells[1].given)
    }

    @Test
    fun noSelectionMeansNoHighlight() {
        assertTrue(SudokuGameUiMapper.cells(newState()).all { it.highlight == CellHighlight.NONE })
    }

    @Test
    fun wrongDigitIsFlaggedAndNotCountedOnPad() {
        val g = newState().let { it.copy(values = it.values.copyOf().also { v -> v[0] = 3 }) }
        val cells = SudokuGameUiMapper.cells(g)
        assertTrue(cells[0].wrong)
        assertFalse(cells[0].given)
        assertFalse(cells[1].wrong)
        val pad = SudokuGameUiMapper.padKeys(g)
        assertEquals(4, pad.size)
        assertEquals(2, pad[0].remaining) // 1s: two givens placed, cells 0 and 15 still open; the wrong 3 in cell 0 is no 1
        assertEquals(0, pad[1].remaining) // all four 2s are givens
        assertFalse(pad[1].enabled)
        assertTrue(pad[0].enabled)
        // a correct digit does count
        val right = newState().let { it.copy(values = it.values.copyOf().also { v -> v[0] = 1 }) }
        assertEquals(1, SudokuGameUiMapper.padKeys(right)[0].remaining)
        assertFalse(SudokuGameUiMapper.cells(right)[0].wrong)
    }

    @Test
    fun eraseOnlyForWrongDigitOrNotes() {
        val base = SudokuGameEngine.select(newState(), 0)
        assertFalse(SudokuGameUiMapper.canErase(base)) // empty, no notes
        val noted = SudokuGameEngine.enterDigit(SudokuGameEngine.toggleNotesMode(base), 2)
        assertTrue(SudokuGameUiMapper.canErase(noted))
        val wrong = SudokuGameEngine.enterDigit(base, 3)
        assertTrue(SudokuGameUiMapper.canErase(wrong))
        assertFalse(SudokuGameUiMapper.canErase(SudokuGameEngine.select(base, 1))) // given
        assertFalse(SudokuGameUiMapper.canErase(SudokuGameEngine.pause(wrong))) // paused
    }

    @Test
    fun noteSlotsFollowBoxShape() {
        assertEquals(1 to 0, SudokuGameUiMapper.noteSlot(3, BoxShape(2, 2)))
        assertEquals(1 to 1, SudokuGameUiMapper.noteSlot(5, BoxShape(2, 3)))
        assertEquals(0 to 2, SudokuGameUiMapper.noteSlot(3, BoxShape(2, 3)))
        assertEquals(2 to 2, SudokuGameUiMapper.noteSlot(9, BoxShape(3, 3)))
        assertEquals(listOf(1, 3), SudokuGameUiMapper.noteDigits(0b0101, 4))
    }

    @Test
    fun uiStateShowsPauseOnlyForUserPause() {
        val g = SudokuGameEngine.pause(newState())
        assertFalse(SudokuGameUiMapper.map(g, 2, userPaused = false, dialog = SudokuDialogUi.ConfirmRestart).paused)
        val ui = SudokuGameUiMapper.map(g, 2, userPaused = true, dialog = SudokuDialogUi.None)
        assertTrue(ui.paused)
        assertFalse(ui.inputEnabled)
        assertEquals(2, ui.hintCharges)
        assertEquals(4, ui.n)
        assertEquals(2, ui.boxCols)
    }

    // ---- view model ----

    private class Rig(scheduler: TestCoroutineScheduler, saved: SudokuGameState?) {
        val repo = FakeSudokuRepository().also { r ->
            if (saved != null) r.saves.value = mapOf(SudokuTier.EASY to saved)
        }
        val inv = FakeInventoryRepository()
        private val time = object : TimeProvider {
            override fun nowMillis(): Long = scheduler.currentTime
            override fun todayEpochDay(): Long = 100L
        }
        private val tracker = AdSessionTracker(time)
        val ads = mockk<ShowSudokuInterstitialIfAllowed>(relaxed = true)
        val analytics = RecordingAnalyticsLogger()
        val vm = SudokuGameViewModel(
            savedState = SavedStateHandle(mapOf(Routes.ARG_SUDOKU_TIER to SudokuTier.EASY.name)),
            startOrResume = StartOrResumeSudoku(repo, UnconfinedTestDispatcher(scheduler)),
            saveGame = SaveSudokuGame(repo),
            useHint = UseSudokuHint(ConsumeBooster(inv)),
            completeSudoku = CompleteSudoku(repo, RecordPlayedToday(FakeStreakRepository(), time), RecordSudokuWon(tracker)),
            showInterstitial = ads,
            observeBoosters = ObserveBoosterBalances(inv),
            observeSettings = ObserveSettings(FakeSettingsRepository()),
            time = time,
            analytics = analytics,
        )
    }

    private fun vmTest(saved: SudokuGameState? = newState(), block: suspend TestScope.(Rig) -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            block(Rig(testScheduler, saved))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun resumesSavedGame() = vmTest { r ->
        assertFalse(r.vm.state.value.loading)
        assertEquals(SudokuTier.EASY, r.vm.state.value.tier)
        assertEquals(16, r.vm.state.value.cells.size)
        r.vm.onAppStopped()
    }

    @Test
    fun winCompletesExactlyOnce() = vmTest { r ->
        r.vm.onCellTap(0)
        r.vm.onDigit(1)
        assertEquals(SudokuDialogUi.None, r.vm.state.value.dialog)
        r.vm.onCellTap(15)
        r.vm.onDigit(1)

        assertTrue(r.vm.state.value.dialog is SudokuDialogUi.Won)
        assertEquals(1, r.repo.progress.value.getValue(SudokuTier.EASY).wins)
        assertNull(r.repo.saves.value[SudokuTier.EASY])

        // finished: further input is ignored, no second completion, no resurrected save
        r.vm.onCellTap(0)
        r.vm.onDigit(1)
        r.vm.onUndo()
        r.vm.onAppStopped()
        assertEquals(1, r.repo.progress.value.getValue(SudokuTier.EASY).wins)
        assertNull(r.repo.saves.value[SudokuTier.EASY])
    }

    @Test
    fun gameOverThenRetryRestartsAndSaves() = vmTest { r ->
        r.vm.onCellTap(0)
        r.vm.onDigit(2)
        r.vm.onDigit(3)
        assertEquals(1, r.vm.state.value.hearts)
        r.vm.onDigit(4)

        assertEquals(SudokuDialogUi.GameOver(3), r.vm.state.value.dialog)
        assertEquals(0, r.vm.state.value.hearts)
        assertNull(r.repo.saves.value[SudokuTier.EASY]) // completed (cleared) once
        assertEquals(0, r.repo.progress.value[SudokuTier.EASY]?.wins ?: 0)

        r.vm.onRestart()
        val ui = r.vm.state.value
        assertEquals(SudokuDialogUi.None, ui.dialog)
        assertEquals(3, ui.hearts)
        assertEquals(0, ui.cells[0].value)
        assertTrue(ui.inputEnabled)
        assertNotNull(r.repo.saves.value[SudokuTier.EASY])
        r.vm.onPauseToggle()
    }

    @Test
    fun hintWithoutChargeEmitsEventAndSpendsNothing() = vmTest { r ->
        r.vm.onCellTap(0)
        r.vm.onHint()
        assertEquals(SudokuGameEvent.NoHintCharge, r.vm.events.first())
        assertEquals(0, r.vm.state.value.cells[0].value)

        r.inv.add(Booster.HINT, 1)
        assertEquals(1, r.vm.state.value.hintCharges)
        r.vm.onHint()
        assertEquals(1, r.vm.state.value.cells[0].value)
        assertEquals(0, r.inv.get(Booster.HINT))
        assertEquals(0, r.vm.state.value.hintCharges)
        r.vm.onPauseToggle()
    }

    @Test
    fun clockTicksOnlyWhileNothingHoldsIt() = vmTest { r ->
        testScheduler.advanceTimeBy(3_001)
        assertEquals("00:03", r.vm.timeText.value)

        r.vm.onPauseToggle()
        assertTrue(r.vm.state.value.paused)
        assertFalse(r.vm.state.value.inputEnabled)
        testScheduler.advanceTimeBy(5_000)
        assertEquals("00:03", r.vm.timeText.value)

        r.vm.onPauseToggle()
        assertFalse(r.vm.state.value.paused)
        testScheduler.advanceTimeBy(2_001)
        assertEquals("00:05", r.vm.timeText.value)

        // app stop holds the clock and saves the elapsed time
        r.vm.onAppStopped()
        testScheduler.advanceTimeBy(4_000)
        assertEquals("00:05", r.vm.timeText.value)
        assertTrue(r.repo.saves.value.getValue(SudokuTier.EASY).elapsedMs >= 5_000L)
        assertFalse(r.vm.state.value.paused) // lifecycle hold does not show the pause overlay

        r.vm.onAppStarted()
        testScheduler.advanceTimeBy(1_001)
        assertEquals("00:06", r.vm.timeText.value)
        r.vm.onPauseToggle()
    }

    @Test
    fun restartDialogHoldsClockAndConfirmResetsIt() = vmTest { r ->
        testScheduler.advanceTimeBy(2_001)
        r.vm.onRestartClick()
        assertEquals(SudokuDialogUi.ConfirmRestart, r.vm.state.value.dialog)
        testScheduler.advanceTimeBy(5_000)
        assertEquals("00:02", r.vm.timeText.value)

        r.vm.onDismissDialog()
        assertEquals(SudokuDialogUi.None, r.vm.state.value.dialog)

        r.vm.onRestartClick()
        r.vm.onRestart()
        assertEquals("00:00", r.vm.timeText.value)
        assertEquals(SudokuDialogUi.None, r.vm.state.value.dialog)
        testScheduler.advanceTimeBy(1_001)
        assertEquals("00:01", r.vm.timeText.value)
        r.vm.onPauseToggle()
    }

    @Test
    fun restartAsksForAdBeforeFreshBoardAndClockStartsAfter() = vmTest { r ->
        testScheduler.advanceTimeBy(2_001)
        r.vm.onRestartClick()
        r.vm.onRestart()
        coVerify(exactly = 1) { r.ads.onRestart(SudokuTier.EASY) } // tier rule (Easy = no ad) lives in the use case
        assertEquals("00:00", r.vm.timeText.value)
        testScheduler.advanceTimeBy(1_001)
        assertEquals("00:01", r.vm.timeText.value)
        r.vm.onPauseToggle()
    }

    @Test
    fun winAdUsesWinCountIncludingTheWinJustRecorded() = vmTest { r ->
        r.repo.setWins(SudokuTier.EASY, 4)
        r.vm.onCellTap(0)
        r.vm.onDigit(1)
        r.vm.onCellTap(15)
        r.vm.onDigit(1)
        assertTrue(r.vm.state.value.dialog is SudokuDialogUi.Won)
        coVerify(exactly = 0) { r.ads.onWin(any(), any()) } // no ad while the dialog is shown
        r.vm.onWinDone()
        coVerify(exactly = 1) { r.ads.onWin(SudokuTier.EASY, 5) } // 5th win
    }

    // ---- analytics ----

    @Test
    fun resumedSaveLogsResumeNotStart() = vmTest { r ->
        assertEquals(listOf("level_resume"), r.analytics.names)
        assertEquals(1L, r.analytics.first("level_resume").params["resumed"])
        r.vm.onAppStopped()
    }

    @Test
    fun backLogsExactlyOneQuitAndBackgroundNever() = vmTest { r ->
        r.vm.onAppStopped()
        r.vm.onAppStarted()
        assertEquals(0, r.analytics.count("level_quit"))
        r.vm.onBack()
        r.vm.onBack()
        assertEquals(1, r.analytics.count("level_quit"))
        assertEquals("system_back", r.analytics.first("level_quit").params["quit_reason"])
        assertEquals("sudoku", r.analytics.first("level_quit").params["mode"])
    }

    @Test
    fun noQuitAfterWinOrGameOver() = vmTest { r ->
        r.vm.onCellTap(0)
        r.vm.onDigit(1)
        r.vm.onCellTap(15)
        r.vm.onDigit(1)
        assertEquals(1, r.analytics.count("level_end"))
        assertEquals("win", r.analytics.first("level_end").params["result"])
        r.vm.onBack()
        assertEquals(0, r.analytics.count("level_quit"))
    }

    @Test
    fun gameOverLogsLoseAndRetryIsRestartNotQuit() = vmTest { r ->
        r.vm.onCellTap(0)
        r.vm.onDigit(2)
        r.vm.onDigit(3)
        r.vm.onDigit(4)
        assertEquals(0, r.analytics.count("level_end")) // loss is held back until the game over truly ends
        r.vm.onRestart()
        assertEquals("lose", r.analytics.first("level_end").params["result"])
        assertEquals(0, r.analytics.count("level_quit"))
        assertEquals(1, r.analytics.count("level_restart"))
        assertEquals(1, r.analytics.count("level_start"))
        r.vm.onPauseToggle()
        assertEquals(1, r.analytics.count("game_pause"))
    }

    private fun gameOver(r: Rig) {
        r.vm.onCellTap(0)
        r.vm.onDigit(2)
        r.vm.onDigit(3)
        r.vm.onDigit(4)
    }

    @Test
    fun reviveRestoresOneHeartResumesAndRecordsNoLoss() = vmTest { r ->
        gameOver(r)
        assertEquals(SudokuDialogUi.GameOver(3), r.vm.state.value.dialog)
        assertNull(r.repo.saves.value[SudokuTier.EASY]) // cleared by the game over

        r.vm.onRevive()
        val ui = r.vm.state.value
        assertEquals(SudokuDialogUi.None, ui.dialog)
        assertEquals(1, ui.hearts)
        assertTrue(ui.inputEnabled)
        assertNotNull(r.repo.saves.value[SudokuTier.EASY]) // save re-established
        assertEquals(0, r.repo.progress.value[SudokuTier.EASY]?.wins ?: 0)
        assertEquals(0, r.analytics.count("level_end")) // no loss analytics
        assertEquals(1, r.analytics.count("level_revive"))
        assertEquals(0, r.analytics.count("level_quit"))

        // a revived game can end again and be revived again (no cap)
        r.vm.onDigit(3)
        assertTrue(r.vm.state.value.dialog is SudokuDialogUi.GameOver)
        r.vm.onRevive()
        assertEquals(1, r.vm.state.value.hearts)
        r.vm.onPauseToggle()
    }

    @Test
    fun reviveIgnoredUnlessGameOverDialogShows() = vmTest { r ->
        r.vm.onRevive()
        assertEquals(3, r.vm.state.value.hearts)
        assertEquals(0, r.analytics.count("level_revive"))
        r.vm.onAppStopped()
    }

    @Test
    fun quitAfterGameOverLogsLossOnceAndNoQuit() = vmTest { r ->
        gameOver(r)
        r.vm.onQuit()
        assertEquals(1, r.analytics.count("level_end"))
        assertEquals("lose", r.analytics.first("level_end").params["result"])
        assertEquals(0, r.analytics.count("level_quit"))
    }

    @Test
    fun engineReviveOnlyFromGameOver() {
        val playing = newState()
        assertTrue(SudokuGameEngine.revive(playing) === playing)
        var g = SudokuGameEngine.select(playing, 0)
        g = SudokuGameEngine.enterDigit(g, 2)
        g = SudokuGameEngine.enterDigit(g, 3)
        g = SudokuGameEngine.enterDigit(g, 4)
        assertEquals(SudokuStatus.GAME_OVER, g.status)
        val revived = SudokuGameEngine.revive(g)
        assertEquals(SudokuStatus.PLAYING, revived.status)
        assertEquals(1, revived.hearts)
        assertEquals(g.mistakes, revived.mistakes)
        assertEquals(g.score, revived.score)
    }

    @Test
    fun gameOverMapperCarriesDialog() {
        val ui = SudokuGameUiMapper.map(newState(), 0, false, SudokuDialogUi.GameOver(3))
        assertEquals(SudokuDialogUi.GameOver(3), ui.dialog)
    }
}
