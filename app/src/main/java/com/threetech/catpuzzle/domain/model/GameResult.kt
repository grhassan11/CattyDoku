package com.threetech.catpuzzle.domain.model

/** Score parts. [total] = max(0, placementPoints + timeBonus - heartPenalty - hintPenalty). */
data class ScoreBreakdown(
    val placementPoints: Int,
    val timeBonus: Int,
    val heartPenalty: Int,
    val hintPenalty: Int,
    val total: Int,
)

/** Final outcome of a level. */
data class GameResult(
    val levelIndex: Int,
    val won: Boolean,
    val score: ScoreBreakdown,
    val heartsLeft: Int,
    val hintsUsed: Int,
    val elapsedSeconds: Int,
)
