package com.threetech.catpuzzle.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.threetech.catpuzzle.data.local.entity.SudokuProgressEntity
import com.threetech.catpuzzle.data.local.entity.SudokuSavedGameEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SudokuProgressDao {
    @Query("SELECT * FROM sudoku_progress")
    abstract fun observeAll(): Flow<List<SudokuProgressEntity>>

    @Query("SELECT * FROM sudoku_progress")
    abstract suspend fun getAll(): List<SudokuProgressEntity>

    @Query("SELECT * FROM sudoku_progress WHERE tierId = :tierId")
    abstract suspend fun get(tierId: Int): SudokuProgressEntity?

    @Upsert
    abstract suspend fun upsert(entity: SudokuProgressEntity)

    /** Atomic read-modify-write. Returns the new plays total. */
    @Transaction
    open suspend fun incrementPlays(tierId: Int): Int {
        val cur = get(tierId) ?: SudokuProgressEntity(tierId, 0, 0, null, 0)
        val next = cur.copy(plays = cur.plays + 1)
        upsert(next)
        return next.plays
    }

    /** Atomic: wins + 1, best time = min, best score = max. Returns the stored row. */
    @Transaction
    open suspend fun recordWin(tierId: Int, timeMs: Long, score: Int): SudokuProgressEntity {
        val cur = get(tierId) ?: SudokuProgressEntity(tierId, 0, 0, null, 0)
        val next = cur.copy(
            wins = cur.wins + 1,
            bestTimeMs = cur.bestTimeMs?.let { minOf(it, timeMs) } ?: timeMs,
            bestScore = maxOf(cur.bestScore, score),
        )
        upsert(next)
        return next
    }
}

@Dao
interface SudokuSavedGameDao {
    @Query("SELECT * FROM sudoku_saved_game WHERE tierId = :tierId")
    suspend fun get(tierId: Int): SudokuSavedGameEntity?

    @Query("SELECT tierId FROM sudoku_saved_game")
    fun observeTierIds(): Flow<List<Int>>

    @Upsert
    suspend fun upsert(entity: SudokuSavedGameEntity)

    @Query("DELETE FROM sudoku_saved_game WHERE tierId = :tierId")
    suspend fun delete(tierId: Int)
}
