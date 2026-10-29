package com.threetech.catpuzzle.data.repository

import com.threetech.catpuzzle.data.local.codec.CodecException
import com.threetech.catpuzzle.data.local.dao.DailyStreakDao
import com.threetech.catpuzzle.data.local.dao.InventoryDao
import com.threetech.catpuzzle.data.local.dao.LevelProgressDao
import com.threetech.catpuzzle.data.local.dao.PuzzleCacheDao
import com.threetech.catpuzzle.data.local.dao.PurchaseGrantDao
import com.threetech.catpuzzle.data.local.dao.SavedGameDao
import com.threetech.catpuzzle.data.local.dao.StreakRewardDao
import com.threetech.catpuzzle.data.local.entity.DailyStreakEntity
import com.threetech.catpuzzle.data.local.mapper.boosterFromStorageKey
import com.threetech.catpuzzle.data.local.mapper.storageKey
import com.threetech.catpuzzle.data.local.mapper.toDomain
import com.threetech.catpuzzle.data.local.mapper.toEntity
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.LevelProgress
import com.threetech.catpuzzle.domain.model.Puzzle
import com.threetech.catpuzzle.domain.model.RewardLine
import com.threetech.catpuzzle.domain.model.SavedGame
import com.threetech.catpuzzle.domain.repository.InventoryRepository
import com.threetech.catpuzzle.domain.repository.ProgressRepository
import com.threetech.catpuzzle.domain.repository.PurchaseGrantRepository
import com.threetech.catpuzzle.domain.repository.PuzzleRepository
import com.threetech.catpuzzle.domain.repository.SavedGameRepository
import com.threetech.catpuzzle.domain.repository.StreakRepository
import com.threetech.catpuzzle.domain.repository.StreakRewardRepository
import com.threetech.catpuzzle.domain.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressRepositoryImpl @Inject constructor(
    private val dao: LevelProgressDao,
) : ProgressRepository {
    override fun observeAll(): Flow<List<LevelProgress>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeDailyCompletions(): Flow<List<LevelProgress>> =
        dao.observeDaily().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(levelIndex: Int): LevelProgress? = dao.get(levelIndex)?.toDomain()

    override suspend fun save(progress: LevelProgress) = dao.upsert(progress.toEntity())
}

@Singleton
class PuzzleRepositoryImpl @Inject constructor(
    private val dao: PuzzleCacheDao,
) : PuzzleRepository {
    /** A row that fails to decode is corrupt: drop it so the level can be rebuilt (cache miss). */
    override suspend fun get(levelIndex: Int): Puzzle? {
        val row = dao.get(levelIndex) ?: return null
        return try {
            row.toDomain()
        } catch (e: CodecException) {
            dao.delete(levelIndex)
            null
        }
    }

    override suspend fun putIfAbsent(puzzle: Puzzle): Boolean = dao.insertIfAbsent(puzzle.toEntity()) != -1L
}

@Singleton
class SavedGameRepositoryImpl @Inject constructor(
    private val dao: SavedGameDao,
) : SavedGameRepository {
    override suspend fun get(levelIndex: Int): SavedGame? = decodeOrDrop(dao.get(levelIndex))

    override suspend fun getLatest(): SavedGame? = decodeOrDrop(dao.getLatest())

    override suspend fun save(game: SavedGame) = dao.upsert(game.toEntity())

    override suspend fun clear(levelIndex: Int) = dao.delete(levelIndex)

    /** Corrupt autosave is dropped (treated as no save) rather than crashing the level start. */
    private suspend fun decodeOrDrop(row: com.threetech.catpuzzle.data.local.entity.SavedGameEntity?): SavedGame? {
        if (row == null) return null
        return try {
            row.toDomain()
        } catch (e: CodecException) {
            dao.delete(row.levelIndex)
            null
        }
    }
}

@Singleton
class StreakRepositoryImpl @Inject constructor(
    private val dao: DailyStreakDao,
) : StreakRepository {
    override fun observePlayedDays(): Flow<Set<Long>> = dao.observeDays().map { it.toSet() }

    override suspend fun recordPlayed(epochDay: Long): Boolean = dao.insert(DailyStreakEntity(epochDay)) != -1L
}

@Singleton
class InventoryRepositoryImpl @Inject constructor(
    private val dao: InventoryDao,
) : InventoryRepository {
    override fun observeAll(): Flow<Map<Booster, Int>> = dao.observeAll().map { rows ->
        val map = HashMap<Booster, Int>()
        for (row in rows) {
            val booster = boosterFromStorageKey(row.boosterType) ?: continue
            map[booster] = row.count
        }
        Booster.entries.associateWith { map[it] ?: 0 }
    }

    override fun observe(booster: Booster): Flow<Int> =
        observeAll().map { it.getValue(booster) }.distinctUntilChanged()

    override suspend fun get(booster: Booster): Int = dao.count(booster.storageKey()) ?: 0

    override suspend fun add(booster: Booster, amount: Int) {
        require(amount > 0) { "amount must be > 0" }
        dao.add(booster.storageKey(), amount)
    }

    override suspend fun consume(booster: Booster, amount: Int): Boolean {
        require(amount > 0) { "amount must be > 0" }
        return dao.consume(booster.storageKey(), amount) > 0
    }
}

@Singleton
class PurchaseGrantRepositoryImpl @Inject constructor(
    private val dao: PurchaseGrantDao,
    private val time: TimeProvider,
) : PurchaseGrantRepository {
    override suspend fun grantOnce(purchaseToken: String, productId: String, booster: Booster, amount: Int): Boolean {
        require(amount > 0) { "amount must be > 0" }
        return dao.grantOnce(purchaseToken, productId, booster.storageKey(), amount, time.nowMillis())
    }
}

@Singleton
class StreakRewardRepositoryImpl @Inject constructor(
    private val dao: StreakRewardDao,
    private val time: TimeProvider,
) : StreakRewardRepository {
    override fun observeClaimedKeys(): Flow<Set<String>> = dao.observeKeys().map { it.toSet() }

    override suspend fun claimOnce(key: String, rewards: List<RewardLine>): Boolean {
        require(rewards.isNotEmpty() && rewards.all { it.amount > 0 }) { "rewards must be non-empty, amounts > 0" }
        return dao.claimOnce(key, rewards.map { it.booster.storageKey() to it.amount }, time.nowMillis())
    }
}
