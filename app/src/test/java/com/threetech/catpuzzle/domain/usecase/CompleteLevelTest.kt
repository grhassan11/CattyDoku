package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.engine.GameFixtures
import com.threetech.catpuzzle.domain.model.GameResult
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.LevelProgress
import com.threetech.catpuzzle.domain.model.SavedGame
import com.threetech.catpuzzle.domain.model.ScoreBreakdown
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteLevelTest {
    private val progress = FakeProgressRepository()
    private val streak = FakeStreakRepository()
    private val saved = FakeSavedGameRepository()
    private val time = FakeTimeProvider(now = 5_000L, today = 20_000L)
    private val useCase = CompleteLevel(progress, streak, saved, time)

    private fun result(
        level: Int = 3,
        won: Boolean = true,
        total: Int = 500,
        heartsLeft: Int = 3,
        hints: Int = 0,
        seconds: Int = 60,
    ) = GameResult(level, won, ScoreBreakdown(total, 0, 0, 0, total), heartsLeft, hints, seconds)

    private fun autosave(level: Int) {
        saved.rows[level] = SavedGame(level, GameState.initial(4), 1_000L, 1L)
    }

    @Test
    fun firstWinWritesProgressStreakAndClearsAutosave() = runTest {
        autosave(3)
        val out = useCase(result(heartsLeft = 2, hints = 1, seconds = 75))

        val expected = LevelProgress(3, 500, 75_000L, 5_000L, mistakes = 1, hintsUsed = 1)
        assertEquals(expected, progress.get(3))
        assertEquals(expected, out.progress)
        assertTrue(out.firstCompletion)
        assertTrue(out.isNewBest)
        assertTrue(out.streakDayAdded)
        assertEquals(setOf(20_000L), streak.observePlayedDays().first())
        assertNull(saved.get(3))
    }

    @Test
    fun replayKeepsBestsAndFirstCompletionTime() = runTest {
        useCase(result(total = 500, seconds = 60, heartsLeft = 3))
        time.now = 9_000L
        val out = useCase(result(total = 300, seconds = 40, heartsLeft = 1, hints = 2))

        val p = progress.get(3)!!
        assertEquals(500, p.bestScore)
        assertEquals(40_000L, p.bestTimeMs) // faster time kept even though score was lower
        assertEquals(5_000L, p.completedAt)
        assertEquals(0, p.mistakes) // belong to the best-score run
        assertEquals(0, p.hintsUsed)
        assertFalse(out.isNewBest)
        assertFalse(out.firstCompletion)
    }

    @Test
    fun higherScoreReplacesRunStats() = runTest {
        useCase(result(total = 300, seconds = 30, heartsLeft = 1, hints = 2))
        val out = useCase(result(total = 700, seconds = 90, heartsLeft = 3, hints = 0))

        val p = progress.get(3)!!
        assertEquals(700, p.bestScore)
        assertEquals(30_000L, p.bestTimeMs)
        assertEquals(0, p.mistakes)
        assertEquals(0, p.hintsUsed)
        assertTrue(out.isNewBest)
    }

    @Test
    fun unchangedRecordIsNotRewritten() = runTest {
        useCase(result(total = 500, seconds = 30))
        val before = progress.saveCount
        useCase(result(total = 400, seconds = 50))
        assertEquals(before, progress.saveCount)
    }

    @Test
    fun secondWinSameDayDoesNotAddStreakDayAgain() = runTest {
        useCase(result(level = 1))
        val out = useCase(result(level = 2))
        assertFalse(out.streakDayAdded)
        assertEquals(setOf(20_000L), streak.observePlayedDays().first())
    }

    @Test
    fun replayOnNewDayDoesNotAddStreakDay() = runTest {
        useCase(result(level = 3))
        time.today = 20_001L
        val out = useCase(result(level = 3, total = 900))

        assertFalse(out.firstCompletion)
        assertFalse(out.streakDayAdded)
        assertEquals(setOf(20_000L), streak.observePlayedDays().first())
    }

    @Test
    fun lossWritesNothingButClearsAutosave() = runTest {
        autosave(3)
        val out = useCase(result(won = false, total = 0, heartsLeft = 0))

        assertNull(out.progress)
        assertFalse(out.streakDayAdded)
        assertNull(progress.get(3))
        assertTrue(streak.observePlayedDays().first().isEmpty())
        assertNull(saved.get(3))
    }

    @Test
    fun otherLevelsAutosaveUntouched() = runTest {
        autosave(3)
        autosave(4)
        useCase(result(level = 3))
        assertNotNull(saved.get(4))
    }

    @Test
    fun mergedWithRejectsOtherLevel() {
        val a = LevelProgress(1, 1, 1, 1, 0, 0)
        val b = LevelProgress(2, 1, 1, 1, 0, 0)
        try {
            a.mergedWith(b)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun gameFixturesAvailable() {
        // sanity: shared fixture used by sibling tests builds
        assertEquals(4, GameFixtures.unique4().size)
    }
}
