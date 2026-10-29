package com.threetech.catpuzzle.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "level_progress")
data class LevelProgressEntity(
    @PrimaryKey val levelIndex: Int,
    val bestScore: Int,
    val bestTimeMs: Long,
    val completedAt: Long,
    val mistakes: Int,
    val hintsUsed: Int,
)

/** Generated puzzle cache. [regions]/[solution] are CellArrayCodec blobs. Rows are never overwritten. */
@Entity(tableName = "puzzle_cache")
class PuzzleCacheEntity(
    @PrimaryKey val levelIndex: Int,
    val size: Int,
    val regions: ByteArray,
    val solution: ByteArray,
    val techniqueLevel: Int,
    val seed: Long,
    val generatorVersion: Int,
)

/**
 * Autosave of one level. [marks]=MarksCodec, [rewardedCells]/[lockedCells]=IndexSetCodec,
 * [undo]=UndoCodec, [charges]=ChargesCodec. [status] is a StableIds code. [elapsedMs] is a duration.
 */
@Entity(tableName = "saved_game")
class SavedGameEntity(
    @PrimaryKey val levelIndex: Int,
    val marks: ByteArray,
    val hearts: Int,
    val status: Int,
    val elapsedMs: Long,
    val hintsUsed: Int,
    val correctPlacements: Int,
    val rewardedCells: ByteArray,
    val lockedCells: ByteArray,
    val undo: ByteArray,
    val charges: ByteArray,
    val updatedAt: Long,
)

/** One row per played local date. */
@Entity(tableName = "daily_streak")
data class DailyStreakEntity(
    @PrimaryKey val epochDay: Long,
)

/** Booster stock. [boosterType] is a StableIds text key ("cat", "hint"). */
@Entity(tableName = "inventory")
data class InventoryEntity(
    @PrimaryKey val boosterType: String,
    @ColumnInfo(name = "count") val count: Int,
)

/**
 * Idempotency ledger for consumable packs: one row per Play purchase token that was already credited to inventory.
 * Written in the SAME transaction as the inventory increment (see PurchaseGrantDao). Never pruned (tiny rows).
 */
@Entity(tableName = "granted_purchase")
data class GrantedPurchaseEntity(
    @PrimaryKey val token: String,
    val productId: String,
    val grantedAtMs: Long,
)

/**
 * Claim ledger for streak milestone rewards: one row per claimed "runStart:absDay" key. Written in the SAME
 * transaction as the inventory credit (see StreakRewardDao). Never pruned (tiny rows).
 */
@Entity(tableName = "streak_reward_claim")
data class StreakRewardClaimEntity(
    @PrimaryKey val claimKey: String,
    val grantedAtMs: Long,
)
