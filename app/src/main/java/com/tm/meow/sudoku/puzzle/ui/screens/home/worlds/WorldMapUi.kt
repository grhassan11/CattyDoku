package com.tm.meow.sudoku.puzzle.ui.screens.home.worlds

import androidx.compose.runtime.Immutable
import com.tm.meow.sudoku.puzzle.domain.model.WorldMapProgress
import com.tm.meow.sudoku.puzzle.domain.model.WorldRules

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
            currentLevel = progress.currentLevel.coerceAtLeast(1),
            starsByLevel = progress.starsByLevel,
            visibleLevels = progress.visibleLevels,
        )
    }
}
