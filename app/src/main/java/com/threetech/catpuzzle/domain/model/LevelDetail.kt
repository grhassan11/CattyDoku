package com.threetech.catpuzzle.domain.model

import com.threetech.catpuzzle.domain.engine.DifficultyCurve

/** Player-facing difficulty bucket of a level, derived from the curve's target technique (1..5). */
enum class LevelDifficulty {
    Easy, Medium, Hard, Expert;

    companion object {
        /** Technique 1-2 = Easy, 3 = Medium, 4 = Hard, 5+ = Expert (values below 1 count as Easy). */
        fun fromTechnique(targetTechnique: Int): LevelDifficulty = when {
            targetTechnique <= 2 -> Easy
            targetTechnique == 3 -> Medium
            targetTechnique == 4 -> Hard
            else -> Expert
        }

        fun of(level: Int): LevelDifficulty = fromTechnique(DifficultyCurve.specFor(level).targetTechnique)
    }
}

/**
 * What the Home level sheet shows for [level]. [completed] false = never won (the current level): no stars / best values.
 * [bestTimeMs] / [bestScore] are null when not completed or when the stored value is not usable (<= 0).
 */
data class LevelDetail(
    val level: Int,
    val worldNumber: Int,
    val boardSize: Int,
    val difficulty: LevelDifficulty,
    val completed: Boolean,
    val stars: Int,
    val bestScore: Int?,
    val bestTimeMs: Long?,
)
