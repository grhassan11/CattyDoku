package com.threetech.catpuzzle.domain.model

/**
 * The puzzle rule a hint leans on. UI maps it to a one-line rule text for the hint overlay.
 * Hints without a rule (mistakes, plain reveal) carry null.
 */
enum class HintRule {
    /** Each row has exactly one cat. */
    ROW,

    /** Each column has exactly one cat. */
    COLUMN,

    /** Each color region has exactly one cat. */
    REGION,

    /** Cats cannot touch, even diagonally. */
    TOUCH,
    ;

    companion object {
        /** Candidate-grid family index (0 region, 1 row, 2 column) -> rule. */
        fun fromFamily(family: Int): HintRule = when (family) {
            0 -> REGION
            1 -> ROW
            else -> COLUMN
        }
    }
}
