package com.threetech.catpuzzle.domain.model

/** Consumable helpers. Persisted ids live in StableIds (data/local/codec): never renumber. */
enum class Booster {
    CAT,
    HINT,

    /** Mouse booster: 3 random X marks on cells that cannot hold a cat. Unlocks at Lv 10 (MouseBoosterPolicy). */
    MOUSE,
}
