package com.threetech.catpuzzle.domain.repository

import com.threetech.catpuzzle.domain.model.SavedGame

interface SavedGameRepository {
    suspend fun get(levelIndex: Int): SavedGame?

    /**
     * Most recently saved LEVEL game (positive levelIndex), for a "continue" entry. Daily autosaves
     * (negative key) are never returned here so a half-played daily cannot hijack the level start.
     */
    suspend fun getLatest(): SavedGame?

    /** Upsert by level. Caller debounces. */
    suspend fun save(game: SavedGame)

    suspend fun clear(levelIndex: Int)
}
