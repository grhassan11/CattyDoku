package com.tm.meow.sudoku.puzzle.domain.repository

import com.tm.meow.sudoku.puzzle.domain.model.SavedGame
import kotlinx.coroutines.flow.Flow

interface SavedGameRepository {
    suspend fun get(levelIndex: Int): SavedGame?

    /**
     * Most recently saved LEVEL game (positive levelIndex), for a "continue" entry. Daily autosaves
     * (negative key) are never returned here so a half-played daily cannot hijack the level start.
     */
    suspend fun getLatest(): SavedGame?

    /** Live autosave of one level (null = none). Re-emits on every save / clear. Corrupt rows read as null. */
    fun observe(levelIndex: Int): Flow<SavedGame?>

    /** Upsert by level. Caller debounces. */
    suspend fun save(game: SavedGame)

    suspend fun clear(levelIndex: Int)
}
