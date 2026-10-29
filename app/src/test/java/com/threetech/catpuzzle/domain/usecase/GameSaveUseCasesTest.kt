package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.engine.GameFixtures
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.GameStatus
import com.threetech.catpuzzle.domain.model.Pos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSaveUseCasesTest {
    private val saved = FakeSavedGameRepository()
    private val puzzles = FakePuzzleRepository()
    private val time = FakeTimeProvider(now = 42L, today = 20_000L)
    private val puzzle = GameFixtures.unique4()

    private fun playing(): GameState = GameState(
        board = GameFixtures.withMarks(4, mapOf(Pos(0, 1) to CellMark.CAT)),
        hearts = 2,
        lockedCells = setOf(1),
        rewardedCells = setOf(1),
        charges = mapOf(Booster.HINT to 1),
    )

    @Test
    fun saveStoresPlayingStateWithDurationAndTimestamp() = runTest {
        SaveGame(saved, time)(1, playing(), 12_345L)
        val g = saved.get(1)!!
        assertEquals(12_345L, g.elapsedMs)
        assertEquals(42L, g.savedAtMs)
        assertEquals(playing(), g.state)
    }

    @Test
    fun negativeElapsedIsClamped() = runTest {
        SaveGame(saved, time)(1, playing(), -5L)
        assertEquals(0L, saved.get(1)!!.elapsedMs)
    }

    @Test
    fun finishedStateClearsInsteadOfSaving() = runTest {
        val save = SaveGame(saved, time)
        save(1, playing(), 1L)
        save(1, playing().copy(status = GameStatus.SOLVED), 2L)
        assertNull(saved.get(1))
    }

    @Test
    fun restoreReturnsPuzzleAndState() = runTest {
        puzzles.putIfAbsent(puzzle)
        SaveGame(saved, time)(1, playing(), 900L)

        val r = RestoreGame(saved, puzzles)(1)!!

        assertEquals(puzzle, r.puzzle)
        assertEquals(playing(), r.saved.state)
        assertEquals(900L, r.saved.elapsedMs)
    }

    @Test
    fun restoreWithoutSaveIsNull() = runTest {
        puzzles.putIfAbsent(puzzle)
        assertNull(RestoreGame(saved, puzzles)(1))
    }

    @Test
    fun restoreClearsSaveWhenPuzzleMissing() = runTest {
        SaveGame(saved, time)(1, playing(), 1L)
        assertNull(RestoreGame(saved, puzzles)(1))
        assertNull(saved.get(1))
    }

    @Test
    fun restoreClearsSaveOnBoardSizeMismatch() = runTest {
        puzzles.putIfAbsent(puzzle)
        SaveGame(saved, time)(1, GameState.initial(5), 1L)
        assertNull(RestoreGame(saved, puzzles)(1))
        assertNull(saved.get(1))
    }

    @Test
    fun latestPicksMostRecentSave() = runTest {
        SaveGame(saved, time)(1, playing(), 1L)
        time.now = 99L
        SaveGame(saved, time)(2, playing(), 1L)
        assertEquals(2, saved.getLatest()!!.levelIndex)
    }

    @Test
    fun recordPlayedTodayIsIdempotent() = runTest {
        val streak = FakeStreakRepository()
        val record = RecordPlayedToday(streak, time)
        assertTrue(record())
        assertFalse(record())
        assertEquals(setOf(20_000L), streak.observePlayedDays().first())
    }

    @Test
    fun observeStreakReflectsPlayedDaysAndToday() = runTest {
        val streak = FakeStreakRepository()
        streak.recordPlayed(19_999L)
        streak.recordPlayed(20_000L)
        val info = ObserveStreak(streak, time)().first()
        assertEquals(2, info.current)
        assertTrue(info.playedToday)
        assertNotNull(info.week.firstOrNull { it.isToday })
    }

    @Test
    fun inventoryFakeContractNeverGoesNegative() = runTest {
        val inv = FakeInventoryRepository()
        assertFalse(inv.consume(Booster.CAT))
        inv.add(Booster.CAT, 2)
        assertTrue(inv.consume(Booster.CAT, 2))
        assertFalse(inv.consume(Booster.CAT))
        assertEquals(0, inv.get(Booster.CAT))
    }
}
