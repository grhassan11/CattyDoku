package com.tm.meow.sudoku.puzzle.ui.screens.game

import com.tm.meow.sudoku.puzzle.domain.analytics.QuitReason
import com.tm.meow.sudoku.puzzle.domain.model.Pos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelAnalyticsTest {

    /** Fixture solution: row -> col = 1,3,0,2. */
    private val solution = listOf(Pos(0, 1), Pos(1, 3), Pos(2, 0), Pos(3, 2))

    private fun vmTest(
        daily: Boolean = false,
        block: suspend TestScope.(GameRig, GameViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val rig = GameRig()
            block(rig, rig.viewModel(daily))
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun GameViewModel.loseLevelOne() {
        onCellDoubleTap(Pos(0, 0))
        onCellDoubleTap(Pos(1, 0))
        onCellDoubleTap(Pos(2, 1))
    }

    @Test
    fun freshLevelLogsStartWithContext() = vmTest { rig, _ ->
        assertEquals(listOf("level_start"), rig.analytics.names)
        val p = rig.analytics.first("level_start").params
        assertEquals("levels", p["mode"])
        assertEquals(1L, p["level_number"])
        assertEquals(1L, p["world"])
        assertEquals(4L, p["board_size"])
        assertEquals(1L, p["attempt_no"])
        assertEquals(0L, p["is_daily"])
    }

    @Test
    fun quitIsLoggedOnceNoMatterHowManyLeaveCalls() = vmTest { rig, vm ->
        vm.onCellDoubleTap(solution[0])
        vm.onAppStopped() // backgrounding is never a quit
        vm.onAppStarted()
        assertEquals(0, rig.analytics.count("level_quit"))
        vm.onUserLeft(QuitReason.TOP_BAR_BACK)
        vm.onUserLeft(QuitReason.CLEARED)
        assertEquals(1, rig.analytics.count("level_quit"))
        val p = rig.analytics.first("level_quit").params
        assertEquals("top_bar_back", p["quit_reason"])
        assertEquals(0L, p["lives_lost"])
    }

    @Test
    fun noQuitAfterWin() = vmTest { rig, vm ->
        for (c in solution) vm.onCellDoubleTap(c)
        advanceUntilIdle()
        vm.onUserLeft(QuitReason.TOP_BAR_BACK)
        assertEquals(0, rig.analytics.count("level_quit"))
        val end = rig.analytics.first("level_end").params
        assertEquals("win", end["result"])
        assertEquals(900L, end["score"])
        assertEquals(1L, end["new_best_score"])
        assertEquals(0L, end["is_replay"]) // first completion
    }

    @Test
    fun noQuitAfterLose() = vmTest { rig, vm ->
        vm.loseLevelOne()
        advanceUntilIdle()
        vm.onUserLeft(QuitReason.TOP_BAR_BACK)
        assertEquals(0, rig.analytics.count("level_quit"))
        val end = rig.analytics.first("level_end").params
        assertEquals("lose", end["result"])
        assertEquals(3L, end["lives_lost"])
        assertFalse("new_best_time" in end)
    }

    @Test
    fun restartIsNotAQuitAndStartsANewAttempt() = vmTest { rig, vm ->
        vm.onCellDoubleTap(solution[0])
        vm.onRestart()
        advanceUntilIdle()
        assertEquals(0, rig.analytics.count("level_quit"))
        assertEquals(1, rig.analytics.count("level_restart"))
        assertEquals(2, rig.analytics.count("level_start"))
        vm.onUserLeft(QuitReason.TOP_BAR_BACK)
        assertEquals(1, rig.analytics.count("level_quit"))
        assertEquals(2L, rig.analytics.first("level_quit").params["attempt_no"])
    }

    @Test
    fun resumingASavedGameLogsResumeNotStart() = vmTest { rig, vm ->
        vm.onCellDoubleTap(solution[0])
        vm.onCellDoubleTap(solution[1])
        advanceUntilIdle()
        rig.analytics.events.clear()
        rig.viewModel()
        advanceUntilIdle()
        assertEquals(listOf("level_resume"), rig.analytics.names)
        assertEquals(1L, rig.analytics.first("level_resume").params["resumed"])
    }

    @Test
    fun reviveIsLoggedAndReopensTheAttempt() = vmTest { rig, vm ->
        vm.loseLevelOne()
        advanceUntilIdle()
        vm.onRevive()
        assertEquals(1, rig.analytics.count("level_revive"))
        vm.onUserLeft(QuitReason.TOP_BAR_BACK)
        assertEquals(1, rig.analytics.count("level_quit"))
    }

    @Test
    fun undoLoggedOnlyWhenSomethingWasUndone() = vmTest { rig, vm ->
        vm.onUndo() // nothing to undo
        assertEquals(0, rig.analytics.count("undo_used"))
        vm.onCellTap(Pos(2, 2))
        vm.onUndo()
        assertEquals(1, rig.analytics.count("undo_used"))
    }

    @Test
    fun boosterCountsIntoTheAttempt() = vmTest { rig, vm ->
        vm.onCatBooster()
        vm.onUserLeft(QuitReason.TOP_BAR_BACK)
        assertEquals(1, rig.analytics.count("booster_used"))
        assertEquals(1L, rig.analytics.first("level_quit").params["boosters_used"])
    }

    @Test
    fun dailyUsesTheSameEventsWithoutALevelNumber() = vmTest(daily = true) { rig, vm ->
        val start = rig.analytics.first("level_start").params
        assertEquals("daily", start["mode"])
        assertEquals(1L, start["is_daily"])
        assertNull(start["level_number"])
        for (c in solution) vm.onCellDoubleTap(c)
        advanceUntilIdle()
        val end = rig.analytics.first("level_end").params
        assertEquals("daily", end["mode"])
        assertNull(end["level_number"])
        assertTrue(rig.analytics.names.none { it == "daily_complete" || it == "daily_start" })
    }
}
