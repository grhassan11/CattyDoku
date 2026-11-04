package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.engine.StreakCalculator
import com.tm.meow.sudoku.puzzle.domain.model.StreakInfo
import com.tm.meow.sudoku.puzzle.domain.repository.StreakRepository
import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Live streak summary. "Today" is read at each emission, so it refreshes when played days change,
 * not at midnight; the UI should re-collect on resume to cross a date boundary.
 */
class ObserveStreak(
    private val repo: StreakRepository,
    private val time: TimeProvider,
) {
    operator fun invoke(): Flow<StreakInfo> =
        repo.observePlayedDays().map { StreakCalculator.compute(it, time.todayEpochDay()) }
}

/** Mark today (local date) as played. Idempotent; true when newly added. */
class RecordPlayedToday(
    private val repo: StreakRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(): Boolean = repo.recordPlayed(time.todayEpochDay())
}
