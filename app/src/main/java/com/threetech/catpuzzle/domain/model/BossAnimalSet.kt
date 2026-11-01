package com.threetech.catpuzzle.domain.model

import kotlin.random.Random

/** The three animal icon sets used instead of cats on boss levels. [count] = icons shipped for the set. */
enum class AnimalSet(val count: Int) {
    ANIMALS(11),
    DOGS(10),
    OCEAN(11),
}

/**
 * What the pieces look like on a level: cats ([set] null, every non-boss level and the daily) or one [AnimalSet] on a
 * boss level. Plain value so screens receive it as state. Pure Kotlin, no Android.
 */
data class PieceSkin(val level: Int, val set: AnimalSet?) {
    val isAnimal: Boolean get() = set != null

    /** Animal index (0 until set.count) for [region]; board cells and the region row both use this. 0 for cats. */
    fun regionIndex(region: Int): Int = set?.let { BossAnimalSet.regionIndex(level, it, region) } ?: 0

    /** The single animal shown on the Cat booster button this level (0 until set.count). 0 for cats. */
    val boosterIndex: Int get() = set?.let { BossAnimalSet.boosterIndex(level, it) } ?: 0

    companion object {
        /** Cats everywhere. */
        fun cats(level: Int = 0) = PieceSkin(level, null)

        /** Animals on a boss level, cats otherwise. */
        fun forLevel(level: Int): PieceSkin =
            if (WorldRules.isBoss(level)) PieceSkin(level, BossAnimalSet.setFor(level)) else cats(level)
    }
}

/**
 * Deterministic animal picks for boss levels. Everything is seeded from the level (and cell), so it is stable across
 * recompositions, undo / redo and launches. Pure Kotlin, no Android.
 */
object BossAnimalSet {
    /** One set per boss level; the Fish Tin boss shape always gets [AnimalSet.OCEAN]. */
    fun setFor(level: Int): AnimalSet {
        if (BossNodeShapes.indexFor(level) == BossNodeShapes.FISH_TIN) return AnimalSet.OCEAN
        val all = AnimalSet.entries
        return all[Random(SET_SEED + level * 7919L).nextInt(all.size)]
    }

    /**
     * Fixed animal for [region] on [level]; in 0 until set.count. A level-seeded shuffle of the set, indexed by region,
     * so regions get distinct animals while region < set.count (boards are at most 9 regions, sets have 10+).
     */
    fun regionIndex(level: Int, set: AnimalSet, region: Int): Int {
        val order = (0 until set.count).shuffled(Random(PIECE_SEED + level * 104729L))
        return order[region.mod(set.count)]
    }

    /** The Cat-booster button animal for [level]; in 0 until set.count. */
    fun boosterIndex(level: Int, set: AnimalSet): Int =
        Random(BOOSTER_SEED + level * 7919L).nextInt(set.count)

    private const val SET_SEED = 0x414E494DL // "ANIM"
    private const val PIECE_SEED = 0x50494543L // "PIEC"
    private const val BOOSTER_SEED = 0x424F4F53L // "BOOS"
}
