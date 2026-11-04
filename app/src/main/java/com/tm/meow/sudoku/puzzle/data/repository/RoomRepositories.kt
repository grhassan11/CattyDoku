package com.tm.meow.sudoku.puzzle.data.repository

import com.tm.meow.sudoku.puzzle.data.local.codec.CodecException
import com.tm.meow.sudoku.puzzle.data.local.dao.DailyStreakDao
import com.tm.meow.sudoku.puzzle.data.local.dao.InventoryDao
import com.tm.meow.sudoku.puzzle.data.local.dao.LevelProgressDao
import com.tm.meow.sudoku.puzzle.data.local.dao.PuzzleCacheDao
import com.tm.meow.sudoku.puzzle.data.local.dao.PurchaseGrantDao
import com.tm.meow.sudoku.puzzle.data.local.dao.SavedGameDao
import com.tm.meow.sudoku.puzzle.data.local.dao.StreakRewardDao
import com.tm.meow.sudoku.puzzle.data.local.entity.DailyStreakEntity
import com.tm.meow.sudoku.puzzle.data.local.mapper.boosterFromStorageKey
import com.tm.meow.sudoku.puzzle.data.local.mapper.storageKey
import com.tm.meow.sudoku.puzzle.data.local.mapper.toDomain
import com.tm.meow.sudoku.puzzle.data.local.mapper.toEntity
import com.tm.meow.sudoku.puzzle.domain.model.Booster
import com.tm.meow.sudoku.puzzle.domain.model.LevelProgress
import com.tm.meow.sudoku.puzzle.domain.model.Puzzle
import com.tm.meow.sudoku.puzzle.domain.model.RewardLine
import com.tm.meow.sudoku.puzzle.domain.model.SavedGame
import com.tm.meow.sudoku.puzzle.domain.repository.InventoryRepository
import com.tm.meow.sudoku.puzzle.domain.repository.ProgressRepository
import com.tm.meow.sudoku.puzzle.domain.repository.PurchaseGrantRepository
import com.tm.meow.sudoku.puzzle.domain.repository.PuzzleRepository
import com.tm.meow.sudoku.puzzle.domain.repository.SavedGameRepository
import com.tm.meow.sudoku.puzzle.domain.repository.StreakRepository
import com.tm.meow.sudoku.puzzle.domain.repository.StreakRewardRepository
import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
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
    private suspend fun decodeOrDrop(row: com.tm.meow.sudoku.puzzle.data.local.entity.SavedGameEntity?): SavedGame? {
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
