package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.model.GameState
import com.tm.meow.sudoku.puzzle.domain.model.GameStatus
import com.tm.meow.sudoku.puzzle.domain.model.SavedGame
import com.tm.meow.sudoku.puzzle.domain.model.UndoEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveLevelResumableTest {
    private val repo = FakeSavedGameRepository()
    private val uc = ObserveLevelResumable(repo)

    private fun game(level: Int, state: GameState) = SavedGame(level, state, 5_000L, 1L)
    private fun fresh() = GameState.initial(4)

    @Test
    fun noSaveIsNotResumable() = runTest { assertFalse(uc(3).first()) }

    @Test
    fun untouchedSaveIsNotResumable() = runTest {
        repo.save(game(3, fresh()))
        assertFalse(uc(3).first())
    }

    @Test
    fun starterCatOnlyIsNotResumableButBoosterCatIs() = runTest {
        repo.save(game(3, fresh().copy(lockedCells = setOf(1), rewardedCells = setOf(1))))
        assertFalse(uc(3).first()) // level <= 20: one locked cell is the free starter
        repo.save(game(30, fresh().copy(lockedCells = setOf(1))))
        assertTrue(uc(30).first()) // no starter beyond level 20: locked = booster use
        repo.save(game(4, fresh().copy(lockedCells = setOf(1, 2))))
        assertTrue(uc(4).first())
    }

    @Test
    fun realMovesAreResumable() = runTest {
        repo.save(game(1, fresh().copy(undoStack = listOf(UndoEntry(emptyList())))))
        repo.save(game(2, fresh().copy(correctPlacements = 1)))
        repo.save(game(3, fresh().copy(hintsUsed = 1)))
        repo.save(game(4, fresh().copy(hearts = 2)))
        for (l in 1..4) assertTrue("level $l", uc(l).first())
    }

    @Test
    fun onlyThatLevelCounts() = runTest {
        repo.save(game(5, fresh().copy(hintsUsed = 1)))
        assertFalse(uc(6).first())
        assertFalse(uc(-5).first())
    }

    @Test
    fun finishedStateIsNotResumable() = runTest {
        repo.save(game(5, fresh().copy(hintsUsed = 1, status = GameStatus.LOST)))
        assertFalse(uc(5).first())
    }

    @Test
    fun clearedAfterWinIsNotResumable() = runTest {
        repo.save(game(5, fresh().copy(hintsUsed = 1)))
        assertTrue(uc(5).first())
        repo.clear(5)
        assertFalse(uc(5).first())
    }
}
