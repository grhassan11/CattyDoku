package com.threetech.catpuzzle.domain.engine

/**
 * The ONE rule for the free starter cat: only numbered levels 1..[STARTER_CAT_THROUGH_LEVEL] get it.
 * Level > through-level: empty start board. Daily Challenge uses negative storage keys, so it never gets it.
 * Pure, no Android.
 */
object StarterCatPolicy {
    const val STARTER_CAT_THROUGH_LEVEL = 10

    fun appliesTo(level: Int, throughLevel: Int = STARTER_CAT_THROUGH_LEVEL): Boolean =
        level in 1..throughLevel
}
