package com.threetech.catpuzzle.data.local.mapper

import com.threetech.catpuzzle.data.local.codec.CodecException
import com.threetech.catpuzzle.domain.engine.GameFixtures
import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.CellChange
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.GameStatus
import com.threetech.catpuzzle.domain.model.LevelProgress
import com.threetech.catpuzzle.domain.model.SavedGame
import com.threetech.catpuzzle.domain.model.UndoEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MappersTest {

    @Test
    fun levelProgressRoundTrip() {
        val p = LevelProgress(12, 840, 61_000L, 1_700_000_000_000L, 1, 2)
        assertEquals(p, p.toEntity().toDomain())
    }

    @Test
    fun puzzleRoundTrip() {
        val puzzle = GameFixtures.unique4()
        val back = puzzle.toEntity().toDomain()
        assertEquals(puzzle, back)
        assertEquals(puzzle.seed, back.seed)
        assertEquals(puzzle.generatorVersion, back.generatorVersion)
    }

    @Test
    fun puzzleRowWithInconsistentSizeFailsAsCodecException() {
        val e = GameFixtures.unique4().toEntity()
        val broken = com.threetech.catpuzzle.data.local.entity.PuzzleCacheEntity(
            e.levelIndex, 5, e.regions, e.solution, e.techniqueLevel, e.seed, e.generatorVersion,
        )
        try {
            broken.toDomain()
            fail("expected CodecException")
        } catch (ex: CodecException) {
            // expected
        }
    }

    private fun richState(): GameState {
        val marks = List(16) { CellMark.EMPTY }.toMutableList()
        marks[1] = CellMark.CAT
        marks[5] = CellMark.CAT
        marks[2] = CellMark.X
        return GameState(
            board = BoardState(4, marks),
            hearts = 2,
            status = GameStatus.PLAYING,
            undoStack = listOf(
                UndoEntry(listOf(CellChange(2, CellMark.EMPTY))),
                UndoEntry(listOf(CellChange(1, CellMark.EMPTY), CellChange(3, CellMark.X))),
            ),
            rewardedCells = setOf(1, 5),
            lockedCells = setOf(5),
            correctPlacements = 2,
            hintsUsed = 1,
            charges = mapOf(Booster.CAT to 2, Booster.HINT to 0),
        )
    }

    @Test
    fun savedGameRoundTripRestoresFullState() {
        val saved = SavedGame(levelIndex = 7, state = richState(), elapsedMs = 93_500L, savedAtMs = 1_700_000_000_123L)
        val back = saved.toEntity().toDomain()
        assertEquals(saved, back)
        assertEquals(setOf(5), back.state.lockedCells)
        assertEquals(setOf(1, 5), back.state.rewardedCells)
        assertEquals(93_500L, back.elapsedMs)
    }

    @Test
    fun savedGameRoundTripForEveryStatus() {
        for (status in GameStatus.entries) {
            val saved = SavedGame(1, GameState.initial(4).copy(status = status), 0L, 5L)
            assertEquals(status, saved.toEntity().toDomain().state.status)
        }
    }

    @Test
    fun corruptSavedGameBlobFailsAsCodecException() {
        val e = SavedGame(1, richState(), 0L, 0L).toEntity()
        val broken = com.threetech.catpuzzle.data.local.entity.SavedGameEntity(
            e.levelIndex, byteArrayOf(1), e.hearts, e.status, e.elapsedMs, e.hintsUsed, e.correctPlacements,
            e.rewardedCells, e.lockedCells, e.undo, e.charges, e.updatedAt,
        )
        try {
            broken.toDomain()
            fail("expected CodecException")
        } catch (ex: CodecException) {
            // expected
        }
    }

    @Test
    fun boosterStorageKeyRoundTrip() {
        for (b in Booster.entries) assertEquals(b, boosterFromStorageKey(b.storageKey()))
    }
}
