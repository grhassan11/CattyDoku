package com.threetech.catpuzzle.domain.engine

/**
 * Which cat art each region shows. Pure function of (puzzle seed, level, region id): stable across
 * recomposition, rotation, undo and process death; nothing is persisted. Variant = index into the
 * UI's list of [UNIQUE] cat images (mapped to drawables in IconArt only).
 */
object CatVariants {
    /** Number of distinct placed-cat images the UI offers. Keep in sync with IconArt. */
    const val UNIQUE = 10

    private const val SALT = 0x4341545641525L // "CATVAR"

    /** Per-puzzle shuffled bag of 0 until [UNIQUE]. */
    fun bag(seed: Long, levelIndex: Int): IntArray {
        val rng = SeededRng(SeededRng.mix(seed, levelIndex.toLong() xor SALT))
        val a = IntArray(UNIQUE) { it }
        rng.shuffle(a)
        return a
    }

    /** Variant for [region]: bag[region % UNIQUE]. All distinct until UNIQUE regions are used, then repeats. */
    fun variantFor(seed: Long, levelIndex: Int, region: Int): Int {
        require(region >= 0) { "region >= 0" }
        return bag(seed, levelIndex)[region % UNIQUE]
    }

    /** Variants for regions 0 until [regionCount]. */
    fun forRegions(seed: Long, levelIndex: Int, regionCount: Int): IntArray {
        val bag = bag(seed, levelIndex)
        return IntArray(regionCount) { bag[it % UNIQUE] }
    }
}
