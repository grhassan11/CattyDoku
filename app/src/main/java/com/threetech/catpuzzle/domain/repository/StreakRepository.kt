package com.threetech.catpuzzle.domain.repository

import kotlinx.coroutines.flow.Flow

/** One entry per played local date (epoch day). */
interface StreakRepository {
    fun observePlayedDays(): Flow<Set<Long>>

    /** Idempotent per day. Returns true when the day was newly added. */
    suspend fun recordPlayed(epochDay: Long): Boolean
}
