package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.engine.BoardValidator
import com.threetech.catpuzzle.domain.model.Analysis
import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.Puzzle
import com.threetech.catpuzzle.domain.model.Settings
import com.threetech.catpuzzle.domain.repository.AppFlagsRepository
import com.threetech.catpuzzle.domain.repository.InventoryRepository
import com.threetech.catpuzzle.domain.repository.ProgressRepository
import com.threetech.catpuzzle.domain.repository.SavedGameRepository
import com.threetech.catpuzzle.domain.repository.SettingsRepository
import com.threetech.catpuzzle.domain.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/*
 * Small use cases the game screen needs on top of the phase 2/3 ones.
 * Pure domain: no Android, no Hilt (bound in di/UseCaseModule).
 */

/** Which level to open on launch: latest autosave, else last played (next one if already won), else 1. */
class ResolveStartLevel(
    private val savedGames: SavedGameRepository,
    private val flags: AppFlagsRepository,
    private val progress: ProgressRepository,
) {
    suspend operator fun invoke(): Int {
        savedGames.getLatest()?.let { return it.levelIndex }
        val last = flags.flags.first().lastPlayedLevel.coerceAtLeast(1)
        return if (progress.get(last) != null) last + 1 else last
    }
}

/**
 * Remember the furthest level being played (drives [ResolveStartLevel], ad pacing and streak rewards). Never moves
 * backwards: replaying a finished level from the Home map must not pull the player's level back.
 */
class MarkLevelPlayed(private val flags: AppFlagsRepository) {
    suspend operator fun invoke(levelIndex: Int) {
        flags.update { if (levelIndex > it.lastPlayedLevel) it.copy(lastPlayedLevel = levelIndex) else it }
    }
}

class ObserveSettings(private val repo: SettingsRepository) {
    operator fun invoke(): Flow<Settings> = repo.settings
}

/** Persist a settings change (settings dialog toggles). The repository applies [transform] atomically. */
class UpdateSettings(private val repo: SettingsRepository) {
    suspend operator fun invoke(transform: (Settings) -> Settings) = repo.update(transform)
}

/** Current booster stock, every [Booster] present (0 when none). */
class GetBoosterCharges(private val inventory: InventoryRepository) {
    suspend operator fun invoke(): Map<Booster, Int> = inventory.observeAll().first()
}

/** Take one charge from the stored inventory. False when stock was already 0. */
class ConsumeBooster(private val inventory: InventoryRepository) {
    suspend operator fun invoke(booster: Booster): Boolean = inventory.consume(booster, 1)
}

/**
 * PLACEHOLDER economy (booster earning is an open product decision): the first time the app runs
 * (no install date stored yet) grant [amount] of each booster and stamp the install date.
 * Later phases that stamp the install date earlier must also grant the starter pack, or drop this.
 */
class SeedStarterBoosters(
    private val flags: AppFlagsRepository,
    private val inventory: InventoryRepository,
    private val time: TimeProvider,
    private val amount: Int = STARTER_AMOUNT,
) {
    suspend operator fun invoke() {
        if (flags.flags.first().installDateEpochDay != null) return
        flags.setInstallDateIfAbsent(time.todayEpochDay())
        for (b in STARTER_BOOSTERS) inventory.add(b, amount)
    }

    companion object {
        const val STARTER_AMOUNT = 5

        /** Same starter stock for every booster (placeholder economy, see class doc). */
        val STARTER_BOOSTERS = listOf(Booster.CAT, Booster.HINT, Booster.MOUSE)
    }
}

/** Board validity snapshot (conflicts, finished units). Wraps the pure validator for the ViewModel. */
class AnalyseBoard {
    operator fun invoke(puzzle: Puzzle, board: BoardState): Analysis = BoardValidator.analyse(puzzle, board)
}

/**
 * Warm the puzzle cache for [levelIndex] (cache-first, generation runs on the use case's dispatcher).
 * Failures are swallowed: prefetch is best-effort and the real load retries.
 */
class PrefetchLevel(private val getOrCreate: GetOrCreatePuzzle) {
    suspend operator fun invoke(levelIndex: Int) {
        try {
            getOrCreate(levelIndex)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            // best effort
        }
    }
}
