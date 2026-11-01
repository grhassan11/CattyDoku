package com.threetech.catpuzzle.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Per-tier stats. [tierId] = SudokuStableIds. [bestTimeMs] null until the first win. [plays] = puzzles started. */
@Entity(tableName = "sudoku_progress")
data class SudokuProgressEntity(
    @PrimaryKey val tierId: Int,
    val wins: Int,
    val plays: Int,
    val bestTimeMs: Long?,
    val bestScore: Int,
)

/**
 * The single active game of one tier (PK = [tierId]). Carries the puzzle itself so a started game never regenerates.
 * [givens]/[solution]/[cellValues] = SudokuDigitsCodec, [cellNotes] = SudokuNotesCodec, [undo] = SudokuUndoCodec.
 * [rating] = SudokuStableIds technique id. Hearts/elapsedMs/score are the live counters.
 */
@Entity(tableName = "sudoku_saved_game")
class SudokuSavedGameEntity(
    @PrimaryKey val tierId: Int,
    val seed: Long,
    val generatorVersion: Int,
    val rating: Int,
    val matchedTier: Boolean,
    val givens: ByteArray,
    val solution: ByteArray,
    val cellValues: ByteArray,
    val cellNotes: ByteArray,
    val undo: ByteArray,
    val elapsedMs: Long,
    val hearts: Int,
    val mistakes: Int,
    val hintsUsed: Int,
    val score: Int,
    val updatedAt: Long,
)
