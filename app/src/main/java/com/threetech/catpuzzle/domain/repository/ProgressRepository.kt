package com.threetech.catpuzzle.domain.repository

import com.threetech.catpuzzle.domain.model.LevelProgress
import kotlinx.coroutines.flow.Flow

interface ProgressRepository {
    /** Completed LEVELS only (positive levelIndex). Daily completions are excluded, see [observeDailyCompletions]. */
    fun observeAll(): Flow<List<LevelProgress>>

    /**
     * Completed daily challenges: rows stored under the negative daily key (see DailyRules.keyForDay),
     * `levelIndex` = that key. Same record shape as a level result.
     */
    fun observeDailyCompletions(): Flow<List<LevelProgress>>

    suspend fun get(levelIndex: Int): LevelProgress?

    /** Replace the stored row. Merge rules live in [LevelProgress.mergedWith] / CompleteLevel. */
    suspend fun save(progress: LevelProgress)
}
