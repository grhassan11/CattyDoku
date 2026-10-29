package com.threetech.catpuzzle.domain.repository

import com.threetech.catpuzzle.domain.model.Puzzle

/** Puzzle cache. First write wins: a cached puzzle is never replaced, whatever the generator version. */
interface PuzzleRepository {
    suspend fun get(levelIndex: Int): Puzzle?

    /** Store [puzzle] unless its level is already cached. Returns true when it was stored. */
    suspend fun putIfAbsent(puzzle: Puzzle): Boolean
}
