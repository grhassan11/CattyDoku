package com.tm.meow.sudoku.puzzle.domain.repository

import com.tm.meow.sudoku.puzzle.domain.model.Puzzle

/** Puzzle cache. First write wins: a cached puzzle is never replaced, whatever the generator version. */
interface PuzzleRepository {
    suspend fun get(levelIndex: Int): Puzzle?

    /** Store [puzzle] unless its level is already cached. Returns true when it was stored. */
    suspend fun putIfAbsent(puzzle: Puzzle): Boolean
}
