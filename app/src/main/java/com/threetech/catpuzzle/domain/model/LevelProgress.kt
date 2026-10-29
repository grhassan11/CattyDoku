package com.threetech.catpuzzle.domain.model

/**
 * Best result of one completed level. A row exists only after a win.
 * [completedAt]: epoch millis of the FIRST completion (never moved by replays).
 * [bestScore] / [bestTimeMs]: independent bests. [mistakes] / [hintsUsed] belong to the best-score run.
 */
data class LevelProgress(
    val levelIndex: Int,
    val bestScore: Int,
    val bestTimeMs: Long,
    val completedAt: Long,
    val mistakes: Int,
    val hintsUsed: Int,
) {
    /** Merge a fresh winning [run] into this stored record. */
    fun mergedWith(run: LevelProgress): LevelProgress {
        require(run.levelIndex == levelIndex) { "level mismatch" }
        val betterScore = run.bestScore > bestScore
        return LevelProgress(
            levelIndex = levelIndex,
            bestScore = maxOf(bestScore, run.bestScore),
            bestTimeMs = minOf(bestTimeMs, run.bestTimeMs),
            completedAt = completedAt,
            mistakes = if (betterScore) run.mistakes else mistakes,
            hintsUsed = if (betterScore) run.hintsUsed else hintsUsed,
        )
    }
}
