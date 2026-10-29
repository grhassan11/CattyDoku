package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.LevelProgress
import com.threetech.catpuzzle.domain.config.AppRemoteConfig
import com.threetech.catpuzzle.domain.config.RemoteConfigRepository
import com.threetech.catpuzzle.domain.model.Puzzle
import com.threetech.catpuzzle.domain.model.RewardLine
import com.threetech.catpuzzle.domain.model.SavedGame
import com.threetech.catpuzzle.domain.repository.InventoryRepository
import com.threetech.catpuzzle.domain.repository.ProgressRepository
import com.threetech.catpuzzle.domain.repository.PuzzleRepository
import com.threetech.catpuzzle.domain.repository.SavedGameRepository
import com.threetech.catpuzzle.domain.repository.StreakRepository
import com.threetech.catpuzzle.domain.repository.StreakRewardRepository
import com.threetech.catpuzzle.domain.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** In-memory fakes for use-case tests. */
class FakeTimeProvider(var now: Long = 1_000L, var today: Long = 20_000L) : TimeProvider {
    override fun nowMillis(): Long = now

    override fun todayEpochDay(): Long = today
}

class FakeProgressRepository : ProgressRepository {
    private val rows = MutableStateFlow<Map<Int, LevelProgress>>(emptyMap())
    var saveCount = 0

    override fun observeAll(): Flow<List<LevelProgress>> =
        rows.map { it.values.filter { p -> p.levelIndex > 0 }.sortedBy { p -> p.levelIndex } }

    override fun observeDailyCompletions(): Flow<List<LevelProgress>> =
        rows.map { it.values.filter { p -> p.levelIndex < 0 }.sortedBy { p -> p.levelIndex } }

    override suspend fun get(levelIndex: Int): LevelProgress? = rows.value[levelIndex]

    override suspend fun save(progress: LevelProgress) {
        saveCount++
        rows.value = rows.value + (progress.levelIndex to progress)
    }
}

class FakePuzzleRepository : PuzzleRepository {
    val rows = LinkedHashMap<Int, Puzzle>()
    var putAttempts = 0

    override suspend fun get(levelIndex: Int): Puzzle? = rows[levelIndex]

    override suspend fun putIfAbsent(puzzle: Puzzle): Boolean {
        putAttempts++
        if (rows.containsKey(puzzle.levelIndex)) return false
        rows[puzzle.levelIndex] = puzzle
        return true
    }
}

class FakeSavedGameRepository : SavedGameRepository {
    val rows = LinkedHashMap<Int, SavedGame>()

    override suspend fun get(levelIndex: Int): SavedGame? = rows[levelIndex]

    override suspend fun getLatest(): SavedGame? =
        rows.values.filter { it.levelIndex > 0 }.maxByOrNull { it.savedAtMs }

    override suspend fun save(game: SavedGame) {
        rows[game.levelIndex] = game
    }

    override suspend fun clear(levelIndex: Int) {
        rows.remove(levelIndex)
    }
}

class FakeStreakRepository : StreakRepository {
    private val days = MutableStateFlow<Set<Long>>(emptySet())

    override fun observePlayedDays(): Flow<Set<Long>> = days

    override suspend fun recordPlayed(epochDay: Long): Boolean {
        if (epochDay in days.value) return false
        days.value = days.value + epochDay
        return true
    }
}

/** Mirrors the real contract: add > 0, consume never goes negative. */
class FakeInventoryRepository : InventoryRepository {
    private val stock = MutableStateFlow<Map<Booster, Int>>(emptyMap())

    override fun observeAll(): Flow<Map<Booster, Int>> =
        stock.map { m -> Booster.entries.associateWith { m[it] ?: 0 } }

    override fun observe(booster: Booster): Flow<Int> = observeAll().map { it.getValue(booster) }.distinctUntilChanged()

    override suspend fun get(booster: Booster): Int = stock.value[booster] ?: 0

    override suspend fun add(booster: Booster, amount: Int) {
        require(amount > 0)
        stock.value = stock.value + (booster to get(booster) + amount)
    }

    override suspend fun consume(booster: Booster, amount: Int): Boolean {
        require(amount > 0)
        val have = get(booster)
        if (have < amount) return false
        stock.value = stock.value + (booster to have - amount)
        return true
    }
}

/** Ledger fake mirroring the Room contract: a key is credited once, repeats return false and change nothing. */
class FakeStreakRewardRepository(private val inventory: InventoryRepository? = null) : StreakRewardRepository {
    private val keys = MutableStateFlow<Set<String>>(emptySet())
    var claimCalls = 0

    override fun observeClaimedKeys(): Flow<Set<String>> = keys

    override suspend fun claimOnce(key: String, rewards: List<RewardLine>): Boolean {
        claimCalls++
        if (key in keys.value) return false
        keys.value = keys.value + key
        for (line in rewards) inventory?.add(line.booster, line.amount)
        return true
    }
}

class FakeRemoteConfigRepository(var config: AppRemoteConfig = AppRemoteConfig()) : RemoteConfigRepository {
    override fun current(): AppRemoteConfig = config

    override fun refresh() = Unit
}
