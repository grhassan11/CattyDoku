package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.engine.DailyRules
import com.threetech.catpuzzle.domain.model.GameResult
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.LevelProgress
import com.threetech.catpuzzle.domain.repository.ProgressRepository
import com.threetech.catpuzzle.domain.repository.SavedGameRepository
import com.threetech.catpuzzle.domain.repository.StreakRepository
import com.threetech.catpuzzle.domain.time.TimeProvider

/**
 * [progress]: stored record after the call (null on a loss). [isNewBest]: first win or higher score.
 * [streakDayAdded]: today was newly added to the streak.
 */
data class CompletionOutcome(
    val progress: LevelProgress?,
    val isNewBest: Boolean,
    val firstCompletion: Boolean,
    val streakDayAdded: Boolean,
)

/**
 * Persist a finished level: merge progress, count today toward the streak (first win of a level, or a daily win), then drop the
 * autosave. A loss only clears the autosave. Streak-on-win-only is a product assumption.
 */
class CompleteLevel(
    private val progress: ProgressRepository,
    private val streak: StreakRepository,
    private val savedGames: SavedGameRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(result: GameResult): CompletionOutcome {
        if (!result.won) {
            savedGames.clear(result.levelIndex)
            return CompletionOutcome(null, isNewBest = false, firstCompletion = false, streakDayAdded = false)
        }

        val run = LevelProgress(
            levelIndex = result.levelIndex,
            bestScore = result.score.total,
            bestTimeMs = result.elapsedSeconds * 1000L,
            completedAt = time.nowMillis(),
            mistakes = GameState.MAX_HEARTS - result.heartsLeft,
            hintsUsed = result.hintsUsed,
        )
        val existing = progress.get(result.levelIndex)
        val merged = existing?.mergedWith(run) ?: run
        if (merged != existing) progress.save(merged)

        // Streak counts only the FIRST completion of a level (read from `existing`, i.e. BEFORE this win was saved).
        // A replay of an already-completed level never adds a streak day. Daily Challenge wins (negative key) still count.
        val countsForStreak = existing == null || DailyRules.isDailyKey(result.levelIndex)
        val dayAdded = countsForStreak && streak.recordPlayed(time.todayEpochDay())
        savedGames.clear(result.levelIndex)

        return CompletionOutcome(
            progress = merged,
            isNewBest = existing == null || run.bestScore > existing.bestScore,
            firstCompletion = existing == null,
            streakDayAdded = dayAdded,
        )
    }
}
