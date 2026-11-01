package com.threetech.catpuzzle.domain.model

import kotlin.random.Random

/**
 * Which of the [COUNT] boss-node shapes a boss level (10, 20, 30, ...) shows. Deterministic: depends only on the
 * level, so it never changes across recompositions or launches. Bosses are dealt in blocks of [COUNT] worlds, each
 * block a seeded shuffle of 0 until [COUNT] (every shape once per block), and the first pick of a block is never
 * the same as the last pick of the block before (adjacent bosses differ). Pure Kotlin, no Android.
 */
object BossNodeShapes {
    const val COUNT = 5

    /** Index of the Fish Tin shape: its boss level always uses the Ocean animal set (see [BossAnimalSet]). */
    const val FISH_TIN = 3

    /** 0 until [COUNT] for any level (non-boss levels map through their world, harmless). */
    fun indexFor(level: Int): Int {
        val world = WorldRules.worldNumber(level) - 1
        val block = world / COUNT
        val pos = world % COUNT
        val perm = blockOrder(block)
        if (pos == 0 && block > 0 && perm[0] == blockOrder(block - 1)[COUNT - 1]) return perm[1]
        if (pos == 1 && block > 0 && perm[0] == blockOrder(block - 1)[COUNT - 1]) return perm[0]
        return perm[pos]
    }

    private fun blockOrder(block: Int): List<Int> =
        (0 until COUNT).toList().shuffled(Random(SEED + block * 7919L))

    private const val SEED = 0x4D454F57L // "MEOW"
}
