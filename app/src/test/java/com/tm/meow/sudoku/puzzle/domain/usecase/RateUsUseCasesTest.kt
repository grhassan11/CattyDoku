package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import com.tm.meow.sudoku.puzzle.domain.model.LevelProgress
import com.tm.meow.sudoku.puzzle.domain.rating.RateAction
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeAppFlagsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateUsUseCasesTest {
    private val time = FakeTimeProvider(now = 1_000_000L)
    private val tracker = AdSessionTracker(time).also { it.startFlowComplete = true }
    private val progress = FakeProgressRepository()
    private val flags = FakeAppFlagsRepository(AppFlags())

    private fun tryShow() = TryShowRateUs(progress, flags, tracker)

    private suspend fun win(level: Int) = progress.save(LevelProgress(level, 500, 1_000L, 1L, 0, 0))

    @Test
    fun notDueBeforeLevel5() = runTest {
        win(4)
        assertFalse(tryShow()())
        assertFalse(flags.flags.first().rateUsShown)
    }

    @Test
    fun dueAfterLevel5AndRecordsShownSoItNeverRepeats() = runTest {
        win(5)
        assertTrue(tryShow()())
        assertTrue(flags.flags.first().rateUsShown)
        assertFalse(tryShow()())
    }

    @Test
    fun heldBackRightAfterAnAdAndNotMarkedShown() = runTest {
        win(6)
        tracker.recordShown()
        assertFalse(tryShow()())
        assertFalse(flags.flags.first().rateUsShown)
        time.now += 130_000L
        assertTrue(tryShow()())
    }

    @Test
    fun heldBackWhileFullScreenAdShowing() = runTest {
        win(6)
        tracker.fullScreenAdShowing = true
        assertFalse(tryShow()())
    }

    @Test
    fun dailyCompletionsDoNotCount() = runTest {
        progress.save(LevelProgress(-20_000, 500, 1_000L, 1L, 0, 0)) // daily rows use negative keys
        assertFalse(tryShow()())
    }

    @Test
    fun dismissCountsUp() = runTest {
        DismissRateUs(flags)()
        DismissRateUs(flags)()
        assertEquals(2, flags.flags.first().rateUsDismissedCount)
    }

    @Test
    fun highStarsMarkRatedLowStarsDoNot() = runTest {
        val submit = SubmitRateUs(flags)
        assertEquals(RateAction.SUBMITTED_LOW, submit(2))
        assertFalse(flags.flags.first().rateUsRated)
        assertEquals(RateAction.STORE_LISTING, submit(5))
        assertTrue(flags.flags.first().rateUsRated)
    }
}
