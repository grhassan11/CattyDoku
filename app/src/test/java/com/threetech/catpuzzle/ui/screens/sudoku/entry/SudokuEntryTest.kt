package com.threetech.catpuzzle.ui.screens.sudoku.entry

import androidx.lifecycle.SavedStateHandle
import com.threetech.catpuzzle.domain.sudoku.engine.SUDOKU_GENERATOR_VERSION
import com.threetech.catpuzzle.domain.sudoku.engine.SudokuGameEngine
import com.threetech.catpuzzle.domain.sudoku.model.SudokuProgress
import com.threetech.catpuzzle.domain.sudoku.model.SudokuPuzzle
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTechnique
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTier
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTierStatus
import com.threetech.catpuzzle.domain.sudoku.usecase.FakeSudokuRepository
import com.threetech.catpuzzle.domain.sudoku.usecase.GetActiveSudokuElapsed
import com.threetech.catpuzzle.domain.sudoku.usecase.ObserveSudokuProgress
import com.threetech.catpuzzle.domain.sudoku.usecase.ResetActiveSudokuGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SudokuEntryTest {

    // ---- mapper ----

    private fun status(
        tier: SudokuTier,
        wins: Int = 0,
        unlocked: Boolean = tier.unlockTier == null,
        active: Boolean = false,
        reqWins: Int = 0,
        bestMs: Long? = null,
        bestScore: Int = 0,
    ) = SudokuTierStatus(tier, SudokuProgress(tier, wins = wins, bestTimeMs = bestMs, bestScore = bestScore), unlocked, active, reqWins)

    @Test
    fun formatTime() {
        assertEquals("00:00", SudokuEntryUiMapper.formatTime(-5))
        assertEquals("01:22", SudokuEntryUiMapper.formatTime(82_900))
        assertEquals("100:00", SudokuEntryUiMapper.formatTime(6_000_000))
    }

    @Test
    fun freshStateLocksHardPlusWithZeroCounters() {
        val ui = SudokuEntryUiMapper.map(SudokuEntryUiMapper.emptyStatuses(), SudokuTier.EASY, null)
        assertEquals(6, ui.rows.size)
        assertEquals(listOf(false, false, true, true, true, true), ui.rows.map { it.locked })
        val needed = ui.rows.filter { it.locked }.map { it.winsNeeded }
        assertEquals(listOf(2, 4, 10, 14), needed)
        assertTrue(ui.rows.filter { it.locked }.all { it.winsHave == 0 })
        assertNull(ui.stats)
        assertFalse(ui.canContinue)
        assertNull(ui.continueTimer)
    }

    @Test
    fun lockedCounterCapsAndTracksPreviousTier() {
        val st = SudokuEntryUiMapper.emptyStatuses().map {
            if (it.tier == SudokuTier.HARD) it.copy(requirementWins = 1) else it
        }
        val hard = SudokuEntryUiMapper.map(st, SudokuTier.EASY, null).rows.first { it.tier == SudokuTier.HARD }
        assertEquals(1, hard.winsHave)
        assertEquals(SudokuTier.MEDIUM, hard.requiredTier)
    }

    @Test
    fun statsAndContinueFromSelectedTier() {
        val st = SudokuEntryUiMapper.emptyStatuses().map {
            if (it.tier == SudokuTier.MEDIUM) status(it.tier, wins = 3, active = true, bestMs = 82_000, bestScore = 900) else it
        }
        val ui = SudokuEntryUiMapper.map(st, SudokuTier.MEDIUM, 82_000)
        assertEquals(SudokuStatsUi(3, "01:22", 900), ui.stats)
        assertTrue(ui.canContinue)
        assertEquals("01:22", ui.continueTimer)
        // other tier selected: no continue
        assertFalse(SudokuEntryUiMapper.map(st, SudokuTier.EASY, null).canContinue)
    }

    @Test
    fun lockedSelectionFallsBackToEasy() {
        val st = SudokuEntryUiMapper.emptyStatuses()
        assertEquals(SudokuTier.EASY, SudokuEntryUiMapper.resolveSelected(st, SudokuTier.EXPERT))
        assertEquals(SudokuTier.MEDIUM, SudokuEntryUiMapper.resolveSelected(st, SudokuTier.MEDIUM))
        assertEquals(SudokuTier.EASY, SudokuEntryUiMapper.resolveSelected(st, null))
    }

    // ---- view model ----

    private val sol4 = intArrayOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 1, 4, 3, 4, 3, 2, 1)

    private fun save(repo: FakeSudokuRepository, tier: SudokuTier, elapsedMs: Long) {
        val puzzle = SudokuPuzzle(
            tier, sol4.copyOf().also { it[0] = 0; it[15] = 0 }, sol4, 1L, SUDOKU_GENERATOR_VERSION, SudokuTechnique.NAKED_SINGLE, true,
        )
        repo.saves.value = repo.saves.value + (tier to SudokuGameEngine.newGame(puzzle).copy(elapsedMs = elapsedMs))
    }

    private fun vmTest(repo: FakeSudokuRepository = FakeSudokuRepository(), block: suspend kotlinx.coroutines.test.TestScope.(FakeSudokuRepository, SudokuEntryViewModel) -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val vm = SudokuEntryViewModel(ObserveSudokuProgress(repo), ResetActiveSudokuGame(repo), GetActiveSudokuElapsed(repo), SavedStateHandle())
            vm.state.onEach { }.launchIn(backgroundScope)
            block(repo, vm)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun selectionPersistsInState() = vmTest { _, vm ->
        assertEquals(SudokuTier.EASY, vm.state.value.selected)
        vm.onSelect(SudokuTier.MEDIUM)
        assertEquals(SudokuTier.MEDIUM, vm.state.value.selected)
    }

    @Test
    fun lockedTapEmitsRequirementAndKeepsSelection() = vmTest { repo, vm ->
        repo.setWins(SudokuTier.MEDIUM, 1)
        vm.onSelect(SudokuTier.HARD)
        val ev = vm.events.first()
        assertEquals(SudokuEntryEvent.Locked(SudokuTier.HARD, SudokuTier.MEDIUM, 2, 1), ev)
        assertEquals(SudokuTier.EASY, vm.state.value.selected)
    }

    @Test
    fun newGameWithoutSaveOpensFreshGame() = vmTest { _, vm ->
        vm.onNewGame()
        assertEquals(SudokuEntryEvent.OpenGame(SudokuTier.EASY, resume = false), vm.events.first())
    }

    @Test
    fun continueShowsSavedTimerAndOpensResume() = vmTest { repo, vm ->
        save(repo, SudokuTier.EASY, 82_000)
        assertTrue(vm.state.value.canContinue)
        assertEquals("01:22", vm.state.value.continueTimer)
        vm.onContinue()
        assertEquals(SudokuEntryEvent.OpenGame(SudokuTier.EASY, resume = true), vm.events.first())
    }

    @Test
    fun newGameOverSaveAsksThenClearsAndOpens() = vmTest { repo, vm ->
        save(repo, SudokuTier.EASY, 5_000)
        vm.onNewGame()
        assertTrue(vm.state.value.confirmNewGame)
        assertTrue(repo.saves.value.containsKey(SudokuTier.EASY))
        vm.onConfirmNewGame()
        assertFalse(vm.state.value.confirmNewGame)
        assertFalse(repo.saves.value.containsKey(SudokuTier.EASY))
        assertEquals(SudokuEntryEvent.OpenGame(SudokuTier.EASY, resume = false), vm.events.first())
    }

    @Test
    fun dismissKeepsSave() = vmTest { repo, vm ->
        save(repo, SudokuTier.EASY, 5_000)
        vm.onNewGame()
        vm.onDismissConfirm()
        assertFalse(vm.state.value.confirmNewGame)
        assertTrue(repo.saves.value.containsKey(SudokuTier.EASY))
    }
}
