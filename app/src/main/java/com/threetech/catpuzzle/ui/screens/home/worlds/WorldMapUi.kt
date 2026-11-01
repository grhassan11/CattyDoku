package com.threetech.catpuzzle.ui.screens.home.worlds

import androidx.compose.runtime.Immutable
import com.threetech.catpuzzle.domain.model.WorldMapProgress
import com.threetech.catpuzzle.domain.model.WorldRules

// TEMP show all worlds - revert (delete this const + the branch in WorldMapUi.from)
private const val TEMP_SHOW_ALL_WORLDS = true

/**
 * Home map state. [currentLevel] = next level to play. [starsByLevel] = 1..3 per won level.
 * [visibleLevels] = last level the map shows (rolling unlock; a "more worlds coming" tail follows it).
 * [loaded] false until the first progress emission (the map draws nothing yet, so it never flashes level 1).
 */
@Immutable
data class WorldMapUi(
    val loaded: Boolean = false,
    val currentLevel: Int = 1,
    val starsByLevel: Map<Int, Int> = emptyMap(),
    val visibleLevels: Int = WorldRules.visibleLevels(1),
) {
    companion object {
        fun from(progress: WorldMapProgress): WorldMapUi = WorldMapUi(
            loaded = true,
            // TEMP review unlock - revert (replace with: progress.currentLevel.coerceAtLeast(1))
            currentLevel = if (TEMP_SHOW_ALL_WORLDS) 100 else progress.currentLevel.coerceAtLeast(1),
            // TEMP review unlock - revert (replace with: progress.starsByLevel). Display-only fake 3 stars L1..199.
            starsByLevel = if (TEMP_SHOW_ALL_WORLDS) (1..199).associateWith { 3 } else progress.starsByLevel,
            // TEMP show all worlds - revert (replace with: progress.visibleLevels)
            visibleLevels = if (TEMP_SHOW_ALL_WORLDS) {
                maxOf(progress.visibleLevels, WorldRules.TEMP_UNLOCK_UP_TO) // TEMP review unlock (was THEME_COUNT * LEVELS_PER_WORLD)
            } else {
                progress.visibleLevels
            },
        )
    }
}
