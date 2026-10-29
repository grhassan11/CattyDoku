package com.threetech.catpuzzle.domain.model

/**
 * Technique behind a hint. [id] matches the LogicSolver technique levels
 * (1 naked single .. 5 look-ahead); [REVEAL] = 0 means no technique, the cat was simply revealed.
 * UI maps [id] to a string resource.
 */
enum class HintTechnique(val id: Int) {
    REVEAL(0),
    NAKED_SINGLE(1),
    CONFINEMENT(2),
    CROWDING_PAIR(3),
    CROWDING_HALL(4),
    LOOKAHEAD(5),
    ;

    companion object {
        fun fromId(id: Int): HintTechnique? = entries.firstOrNull { it.id == id }
    }
}
