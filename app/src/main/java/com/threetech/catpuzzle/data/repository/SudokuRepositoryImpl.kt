package com.threetech.catpuzzle.data.repository

import com.threetech.catpuzzle.data.local.codec.CodecException
import com.threetech.catpuzzle.data.local.codec.SudokuStableIds
import com.threetech.catpuzzle.data.local.dao.SudokuProgressDao
import com.threetech.catpuzzle.data.local.dao.SudokuSavedGameDao
import com.threetech.catpuzzle.data.local.entity.SudokuSavedGameEntity
import com.threetech.catpuzzle.data.local.mapper.toDomain
import com.threetech.catpuzzle.data.local.mapper.toDomainOrNull
import com.threetech.catpuzzle.data.local.mapper.toEntity
import com.threetech.catpuzzle.domain.sudoku.model.SudokuGameState
import com.threetech.catpuzzle.domain.sudoku.model.SudokuProgress
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTier
import com.threetech.catpuzzle.domain.sudoku.repository.SudokuRepository
import com.threetech.catpuzzle.domain.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SudokuRepositoryImpl @Inject constructor(
    private val progressDao: SudokuProgressDao,
    private val savedDao: SudokuSavedGameDao,
    private val time: TimeProvider,
) : SudokuRepository {

    override fun observeProgress(): Flow<Map<SudokuTier, SudokuProgress>> =
        progressDao.observeAll().map { rows -> withDefaults(rows.mapNotNull { it.toDomainOrNull() }) }

    override fun observeActiveTiers(): Flow<Set<SudokuTier>> =
        savedDao.observeTierIds()
            .map { ids -> ids.mapNotNull { SudokuStableIds.tierOrNull(it) }.toSet() }
            .distinctUntilChanged()

    override suspend fun getProgress(tier: SudokuTier): SudokuProgress =
        progressDao.get(SudokuStableIds.tierId(tier))?.toDomainOrNull() ?: SudokuProgress(tier)

    override suspend fun winsByTier(): Map<SudokuTier, Int> =
        withDefaults(progressDao.getAll().mapNotNull { it.toDomainOrNull() }).mapValues { it.value.wins }

    override suspend fun nextPlayIndex(tier: SudokuTier): Int = progressDao.incrementPlays(SudokuStableIds.tierId(tier))

    override suspend fun recordWin(tier: SudokuTier, timeMs: Long, score: Int): SudokuProgress =
        progressDao.recordWin(SudokuStableIds.tierId(tier), timeMs, score).toDomainOrNull() ?: SudokuProgress(tier)

    override suspend fun getActiveGame(tier: SudokuTier): SudokuGameState? =
        decodeOrDrop(savedDao.get(SudokuStableIds.tierId(tier)))

    override suspend fun saveActiveGame(state: SudokuGameState) = savedDao.upsert(state.toEntity(time.nowMillis()))

    override suspend fun clearActiveGame(tier: SudokuTier) = savedDao.delete(SudokuStableIds.tierId(tier))

    /** Corrupt save is dropped (treated as no save) rather than crashing the start. */
    private suspend fun decodeOrDrop(row: SudokuSavedGameEntity?): SudokuGameState? {
        if (row == null) return null
        return try {
            row.toDomain()
        } catch (e: CodecException) {
            savedDao.delete(row.tierId)
            null
        }
    }

    private fun withDefaults(rows: List<SudokuProgress>): Map<SudokuTier, SudokuProgress> {
        val byTier = rows.associateBy { it.tier }
        return SudokuTier.entries.associateWith { byTier[it] ?: SudokuProgress(it) }
    }
}
