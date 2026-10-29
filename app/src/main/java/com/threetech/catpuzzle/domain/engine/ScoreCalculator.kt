package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.ScoreBreakdown

/**
 * Every score tunable, in one place.
 * Time bonus is linear: full [timeBonusMax] at 0 s, zero at [timeBudgetSecondsPerCat] * size seconds.
 */
data class ScoreConfig(
    val pointsPerCorrectPlacement: Int = 100,
    val timeBonusMax: Int = 500,
    val timeBudgetSecondsPerCat: Int = 20,
    val heartPenalty: Int = 150,
    val hintPenalty: Int = 100,
) {
    companion object {
        val DEFAULT = ScoreConfig()
    }
}

/** What the calculator needs. [elapsedSeconds] comes from the caller (domain is clock-free). */
data class ScoreInput(
    val size: Int,
    val correctPlacements: Int,
    val heartsLost: Int,
    val hintsUsed: Int,
    val elapsedSeconds: Int,
    val won: Boolean,
)

/**
 * Deterministic pure score: placements + time bonus (win only) - hearts lost - hints used, floored at 0.
 * Monotone: faster never scores lower, more hints or hearts lost never score higher.
 */
class ScoreCalculator(private val config: ScoreConfig = ScoreConfig.DEFAULT) {

    fun compute(input: ScoreInput): ScoreBreakdown {
        val placement = input.correctPlacements.coerceAtLeast(0).toLong() * config.pointsPerCorrectPlacement
        val bonus = if (input.won) timeBonus(input.size, input.elapsedSeconds) else 0
        val hearts = input.heartsLost.coerceAtLeast(0).toLong() * config.heartPenalty
        val hints = input.hintsUsed.coerceAtLeast(0).toLong() * config.hintPenalty
        val total = (placement + bonus - hearts - hints).coerceIn(0L, Int.MAX_VALUE.toLong())
        return ScoreBreakdown(
            placementPoints = placement.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            timeBonus = bonus,
            heartPenalty = hearts.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            hintPenalty = hints.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            total = total.toInt(),
        )
    }

    private fun timeBonus(size: Int, elapsedSeconds: Int): Int {
        val budget = (config.timeBudgetSecondsPerCat.toLong() * size).coerceAtLeast(1L)
        val left = (budget - elapsedSeconds.coerceAtLeast(0)).coerceAtLeast(0L)
        return (config.timeBonusMax.toLong() * left / budget).toInt()
    }
}
