package com.threetech.catpuzzle.ui.screens.game

import app.cash.turbine.test
import com.threetech.catpuzzle.domain.engine.DailyRules
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.usecase.ObserveCompletedDays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Daily Challenge mode of [GameViewModel] (nav arg `daily=true`). Today in the rig = epoch day 20_000. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelDailyTest {

    /** Solution of the fixture: row -> col = 1,3,0,2. */
    private val solution = listOf(Pos(0, 1), Pos(1, 3), Pos(2, 0), Pos(3, 2))
    private val today = 20_000L
    private val key = DailyRules.keyForDay(today)

    private fun dailyTest(
        rig: GameRig = GameRig(),
        block: suspend TestScope.(GameRig, GameViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            block(rig, rig.viewModel(daily = true))
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun GameViewModel.ready(): GameContentUi = (state.value as GameUiState.Ready).content

    @Test
    fun loadsTodaysDailyWithoutTouchingLevelState() = dailyTest { rig, vm ->
        val c = vm.ready()
        assertTrue(c.isDaily)
        assertEquals(today, c.dailyEpochDay)
        assertEquals(0, c.levelNumber)
        assertEquals(3, c.hearts)
        assertEquals(key, rig.dailyGenerator.specs.single().levelIndex)
        assertNotNull(rig.puzzles.rows[key])
        // level-side state untouched
        assertEquals(0, rig.flags.flags.first().lastPlayedLevel)
        assertTrue(rig.generator.levels.isEmpty()) // no next-level prefetch
        // streak is win-only: loading a daily does not count the day yet
        assertTrue(rig.streak.observePlayedDays().first().isEmpty())
    }

    @Test
    fun normalModeIsNotDaily() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val rig = GameRig()
            val c = rig.viewModel().ready()
            assertFalse(c.isDaily)
            assertNull(c.dailyEpochDay)
            assertEquals(1, c.levelNumber)
            assertTrue(rig.dailyGenerator.specs.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun winRecordsCompletionForThatDateNotLevelProgress() = dailyTest { rig, vm ->
        vm.events.test {
            for (p in solution) vm.onCellDoubleTap(p)
            advanceUntilIdle()
            val items = cancelAndConsumeRemainingEvents()
                .mapNotNull { (it as? app.cash.turbine.Event.Item<*>)?.value }
            assertEquals(1, items.count { it is GameUiEvent.LevelWon })
            assertEquals(1, items.count { it == GameUiEvent.Sfx(SfxKind.AWARD) })
            assertTrue(items.none { it == GameUiEvent.Sfx(SfxKind.WIN) })
        }
        assertTrue(vm.ready().outcome is OutcomeUi.Won)
        assertEquals(setOf(today), ObserveCompletedDays(rig.progress)().first())
        assertTrue(rig.progress.observeAll().first().isEmpty())
        assertNull(rig.saved.rows[key])
        assertEquals(0, rig.flags.flags.first().lastPlayedLevel)
    }

    @Test
    fun winAfterMidnightStillCompletesTheDateTheDailyBelongsTo() = dailyTest { rig, vm ->
        rig.time.today = today + 1 // midnight passes mid-game
        for (p in solution) vm.onCellDoubleTap(p)
        advanceUntilIdle()
        assertEquals(setOf(today), ObserveCompletedDays(rig.progress)().first())
        assertTrue((today + 1) in rig.streak.observePlayedDays().first()) // streak counts the real day
    }

    @Test
    fun nextLevelIsIgnoredInDailyMode() = dailyTest { _, vm ->
        for (p in solution) vm.onCellDoubleTap(p)
        advanceUntilIdle()
        val before = vm.state.value
        vm.onNextLevel()
        assertEquals(before, vm.state.value)
        assertTrue(vm.ready().isDaily)
    }

    @Test
    fun loseThenRetryRestartsTheSameDaily() = dailyTest { rig, vm ->
        vm.onCellDoubleTap(Pos(0, 0))
        vm.onCellDoubleTap(Pos(1, 0))
        vm.onCellDoubleTap(Pos(2, 1))
        advanceUntilIdle()
        assertTrue(vm.ready().outcome is OutcomeUi.Lost)
        assertTrue(rig.progress.observeDailyCompletions().first().isEmpty())

        vm.onRestart()
        advanceUntilIdle()
        val c = vm.ready()
        assertNull(c.outcome)
        assertEquals(3, c.hearts)
        assertEquals(today, c.dailyEpochDay)
        assertEquals(1, rig.dailyGenerator.specs.size) // same puzzle, not regenerated
    }

    @Test
    fun halfPlayedDailyResumesAndNeverHijacksLevelStart() = dailyTest { rig, vm ->
        vm.onCellDoubleTap(solution[0])
        vm.onCellDoubleTap(solution[1])
        advanceUntilIdle()
        assertNotNull(rig.saved.rows[key])

        val resumed = rig.viewModel(daily = true).ready()
        assertEquals(CellMarkUi.CAT, resumed.board.cell(0, 1).mark)
        assertEquals(CellMarkUi.CAT, resumed.board.cell(1, 3).mark)
        assertEquals(CellMarkUi.EMPTY, resumed.board.cell(2, 0).mark)
        assertTrue(resumed.isDaily)
        assertEquals(1, rig.dailyGenerator.specs.size) // cached, not regenerated

        // the level flow still starts at level 1 even though the daily autosave is the newest
        val level = rig.viewModel().ready()
        assertFalse(level.isDaily)
        assertEquals(1, level.levelNumber)
        assertEquals(CellMarkUi.EMPTY, level.board.cell(0, 1).mark)
    }

    @Test
    fun generationFailureShowsFailedThenRetrySucceeds() = dailyTest(GameRig(dailyGenerator = RecordingDailyGenerator(succeed = false))) { rig, vm ->
        vm.state.test {
            var item = awaitItem()
            while (item !is GameUiState.Failed) item = awaitItem()
            rig.puzzles.rows[key] = fixturePuzzle(key)
            vm.onRetryLoad()
            while (item !is GameUiState.Ready) item = awaitItem()
            assertTrue((item as GameUiState.Ready).content.isDaily)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun restartInDailyKeepsDailyModeAndSave() = dailyTest { rig, vm ->
        vm.onCellDoubleTap(solution[0])
        vm.onRestart()
        advanceUntilIdle()
        assertTrue(vm.ready().isDaily)
        assertEquals(CellMarkUi.EMPTY, vm.ready().board.cell(0, 1).mark)
        assertNotNull(rig.saved.rows[key])
    }
}
