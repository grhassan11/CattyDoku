package com.threetech.catpuzzle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.threetech.catpuzzle.data.local.entity.DailyStreakEntity
import com.threetech.catpuzzle.data.local.entity.GrantedPurchaseEntity
import com.threetech.catpuzzle.data.local.entity.InventoryEntity
import com.threetech.catpuzzle.data.local.entity.LevelProgressEntity
import com.threetech.catpuzzle.data.local.entity.PuzzleCacheEntity
import com.threetech.catpuzzle.data.local.entity.SavedGameEntity
import com.threetech.catpuzzle.data.local.entity.StreakRewardClaimEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LevelProgressDao {
    @Query("SELECT * FROM level_progress WHERE levelIndex = :levelIndex")
    suspend fun get(levelIndex: Int): LevelProgressEntity?

    /** Levels only (positive key). */
    @Query("SELECT * FROM level_progress WHERE levelIndex > 0 ORDER BY levelIndex")
    fun observeAll(): Flow<List<LevelProgressEntity>>

    /** Daily completions only (negative key = -epochDay). */
    @Query("SELECT * FROM level_progress WHERE levelIndex < 0 ORDER BY levelIndex DESC")
    fun observeDaily(): Flow<List<LevelProgressEntity>>

    @Upsert
    suspend fun upsert(entity: LevelProgressEntity)
}

@Dao
interface PuzzleCacheDao {
    @Query("SELECT * FROM puzzle_cache WHERE levelIndex = :levelIndex")
    suspend fun get(levelIndex: Int): PuzzleCacheEntity?

    /** IGNORE: an existing row wins. Returns the new rowid, or -1 when ignored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: PuzzleCacheEntity): Long

    /** Only for rows that fail to decode (corruption). Never used to refresh a valid puzzle. */
    @Query("DELETE FROM puzzle_cache WHERE levelIndex = :levelIndex")
    suspend fun delete(levelIndex: Int)
}

@Dao
interface SavedGameDao {
    @Query("SELECT * FROM saved_game WHERE levelIndex = :levelIndex")
    suspend fun get(levelIndex: Int): SavedGameEntity?

    /** Levels only: daily autosaves use a negative key and must never be "latest". */
    @Query("SELECT * FROM saved_game WHERE levelIndex > 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatest(): SavedGameEntity?

    @Upsert
    suspend fun upsert(entity: SavedGameEntity)

    @Query("DELETE FROM saved_game WHERE levelIndex = :levelIndex")
    suspend fun delete(levelIndex: Int)
}

@Dao
interface DailyStreakDao {
    /** Returns -1 when the day already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DailyStreakEntity): Long

    @Query("SELECT epochDay FROM daily_streak ORDER BY epochDay")
    fun observeDays(): Flow<List<Long>>
}

@Dao
abstract class InventoryDao {
    @Query("SELECT * FROM inventory")
    abstract fun observeAll(): Flow<List<InventoryEntity>>

    @Query("SELECT `count` FROM inventory WHERE boosterType = :type")
    abstract suspend fun count(type: String): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(entity: InventoryEntity): Long

    /** Saturates at 999999 so a bug can never overflow the Int column. */
    @Query("UPDATE inventory SET `count` = MIN(`count` + :amount, 999999) WHERE boosterType = :type")
    abstract suspend fun increment(type: String, amount: Int): Int

    /**
     * Single atomic statement: decrements only when enough stock exists, so the count can never go
     * negative. Returns rows changed (1 = consumed, 0 = refused).
     */
    @Query("UPDATE inventory SET `count` = `count` - :amount WHERE boosterType = :type AND `count` >= :amount")
    abstract suspend fun consume(type: String, amount: Int): Int

    @Transaction
    open suspend fun add(type: String, amount: Int) {
        insertIgnore(InventoryEntity(type, 0))
        increment(type, amount)
    }
}

/**
 * Atomic, idempotent credit of a purchased pack. Same inventory SQL as [InventoryDao] (kept in one DAO so a single
 * @Transaction covers the ledger row and the stock change).
 */
@Dao
abstract class PurchaseGrantDao {
    /** IGNORE: returns -1 when the token is already recorded. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertGrant(entity: GrantedPurchaseEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertInventoryIgnore(entity: InventoryEntity): Long

    /** Same saturating increment as InventoryDao.increment. */
    @Query("UPDATE inventory SET `count` = MIN(`count` + :amount, 999999) WHERE boosterType = :type")
    abstract suspend fun incrementInventory(type: String, amount: Int): Int

    /** True = credited now; false = token already granted (nothing changed). */
    @Transaction
    open suspend fun grantOnce(token: String, productId: String, boosterType: String, amount: Int, nowMs: Long): Boolean {
        if (insertGrant(GrantedPurchaseEntity(token, productId, nowMs)) == -1L) return false
        insertInventoryIgnore(InventoryEntity(boosterType, 0))
        incrementInventory(boosterType, amount)
        return true
    }
}

/**
 * Atomic, idempotent credit of a streak milestone reward: ledger row + every booster line in ONE transaction.
 * Same saturating inventory SQL as [InventoryDao] (kept here so a single @Transaction covers both).
 */
@Dao
abstract class StreakRewardDao {
    @Query("SELECT claimKey FROM streak_reward_claim")
    abstract fun observeKeys(): Flow<List<String>>

    /** IGNORE: returns -1 when the key is already recorded. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertClaim(entity: StreakRewardClaimEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertInventoryIgnore(entity: InventoryEntity): Long

    /** Same saturating increment as InventoryDao.increment. */
    @Query("UPDATE inventory SET `count` = MIN(`count` + :amount, 999999) WHERE boosterType = :type")
    abstract suspend fun incrementInventory(type: String, amount: Int): Int

    /** [lines] = (boosterType storage key, amount > 0). True = credited now; false = key already claimed. */
    @Transaction
    open suspend fun claimOnce(key: String, lines: List<Pair<String, Int>>, nowMs: Long): Boolean {
        if (insertClaim(StreakRewardClaimEntity(key, nowMs)) == -1L) return false
        for ((type, amount) in lines) {
            insertInventoryIgnore(InventoryEntity(type, 0))
            incrementInventory(type, amount)
        }
        return true
    }
}
