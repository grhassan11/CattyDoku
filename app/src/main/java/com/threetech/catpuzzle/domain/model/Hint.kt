package com.threetech.catpuzzle.domain.model

/** What is wrong with a player mark. */
enum class MistakeKind { WRONG_CAT, X_ON_SOLUTION }

/** Typed hint, no text. UI renders it. */
sealed interface Hint {
    /** [cell] holds a wrong cat, or an X on a solution cell. */
    data class Mistake(val cell: Pos, val kind: MistakeKind) : Hint

    /**
     * Put a cat on [target]. [technique] is REVEAL when no logic step applied.
     * [rule] / [focus]: the rule and the full unit cells behind it (overlay spotlight); null / empty for REVEAL.
     */
    data class Place(
        val target: Pos,
        val technique: HintTechnique,
        val rule: HintRule? = null,
        val focus: List<Pos> = emptyList(),
    ) : Hint

    /** Cells in [targets] cannot hold a cat. [reasons]: candidate cells of the units that force it.
     * [rule] / [focus]: the rule and the full unit cells behind the step (overlay spotlight). */
    data class Eliminate(
        val targets: List<Pos>,
        val reasons: List<Pos>,
        val technique: HintTechnique,
        val rule: HintRule? = null,
        val focus: List<Pos> = emptyList(),
    ) : Hint

    /** Nothing to hint (board solved). */
    data object None : Hint
}
