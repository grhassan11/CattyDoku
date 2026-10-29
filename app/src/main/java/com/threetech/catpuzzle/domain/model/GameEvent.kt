package com.threetech.catpuzzle.domain.model

/** One-shot outcome of a board operation. Plain data; UI turns these into haptics / sounds / dialogs. */
sealed interface GameEvent {
    /** A cat was placed on a non-solution cell; one heart lost. */
    data class WrongPlacement(val cell: Pos, val heartsLeft: Int) : GameEvent

    /** Cats clash after a placement. [cells] sorted by flat index. */
    data class Conflict(val cells: List<Pos>) : GameEvent

    data object Solved : GameEvent

    data object Lost : GameEvent
}
