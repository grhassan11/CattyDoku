package com.threetech.catpuzzle.domain.model

/**
 * Level -> world mapping. Levels are endless: worlds repeat in the same order after the last authored one.
 * [worldNumber] counts up forever (the visible "World N" label); [themeIndex] cycles 0 until [THEME_COUNT].
 * Pure Kotlin, no Android.
 */
object WorldRules {
    const val LEVELS_PER_WORLD = 10
    const val THEME_COUNT = 10

    /** 1-based, unbounded: levels 1-10 = 1, 11-20 = 2, ... 101-110 = 11. */
    fun worldNumber(level: Int): Int = (level.coerceAtLeast(1) - 1) / LEVELS_PER_WORLD + 1

    /** 0-based index of the authored theme: ((level-1)/10) % 10. */
    fun themeIndex(level: Int): Int = (worldNumber(level) - 1) % THEME_COUNT

    /** 1..[LEVELS_PER_WORLD]. */
    fun levelInWorld(level: Int): Int = (level.coerceAtLeast(1) - 1) % LEVELS_PER_WORLD + 1

    /** First level (1-based) of [worldNumber]. */
    fun firstLevelOf(worldNumber: Int): Int = (worldNumber.coerceAtLeast(1) - 1) * LEVELS_PER_WORLD + 1

    fun isBoss(level: Int): Boolean = levelInWorld(level) == LEVELS_PER_WORLD

    // TEMP unlock first 50 levels (difficulty testing): Home map nodes 1..this are tappable/playable regardless of progress. Set 0 to remove.
    const val TEMP_UNLOCK_UP_TO = 50

    /** Worlds open on a fresh install (Lv 1-60). */
    const val INITIAL_OPEN_WORLDS = 6

    /** Worlds added each time the player reaches the first level of the last open world. */
    const val UNLOCK_STEP_WORLDS = 3

    /**
     * Rolling unlock: number of worlds the Home map shows for a player at [currentLevel] = the smallest
     * 6 + 3k that is strictly greater than the current world. Lv 1-50 -> 6, Lv 51-80 -> 9, Lv 81-110 -> 12, ...
     * (reaching the first level of the last open world opens the next [UNLOCK_STEP_WORLDS]).
     */
    fun visibleWorlds(currentLevel: Int): Int {
        val current = worldNumber(currentLevel)
        if (current < INITIAL_OPEN_WORLDS) return INITIAL_OPEN_WORLDS
        return INITIAL_OPEN_WORLDS + UNLOCK_STEP_WORLDS * ((current - INITIAL_OPEN_WORLDS) / UNLOCK_STEP_WORLDS + 1)
    }

    /** Last level shown on the map for a player at [currentLevel] (worlds x 10). */
    fun visibleLevels(currentLevel: Int): Int = visibleWorlds(currentLevel) * LEVELS_PER_WORLD
}

/** Earned stars (1..3) for a finished level. No star field is stored; derived from the best-score run. */
object LevelStars {
    const val MAX = 3

    /** 3 = clean run (no mistakes, no hints); 2 = one or two slips; 1 = more. A win is always >= 1. */
    fun of(progress: LevelProgress): Int {
        val slips = progress.mistakes + progress.hintsUsed
        return when {
            slips <= 0 -> 3
            slips <= 2 -> 2
            else -> 1
        }
    }
}

/**
 * Home map progress. [currentLevel] = lowest level not yet won (next to play). [starsByLevel] = won levels only.
 * Node state: won -> Done(stars), == current -> Current, else Locked (up to [visibleLevels]).
 */
data class WorldMapProgress(
    val currentLevel: Int,
    val starsByLevel: Map<Int, Int>,
) {
    /** Last level the map shows (rolling unlock, see [WorldRules.visibleWorlds]); a "more worlds coming" tail follows it. */
    val visibleLevels: Int get() = WorldRules.visibleLevels(currentLevel)

    companion object {
        val EMPTY = WorldMapProgress(currentLevel = 1, starsByLevel = emptyMap())
    }
}
