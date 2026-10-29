package com.threetech.catpuzzle.domain.model

/**
 * Autosave snapshot of an in-progress level.
 * [elapsedMs] is a play DURATION (not a timestamp), so it survives clock changes and process death.
 * [savedAtMs]: epoch millis of the save, used only to pick the most recent one.
 */
data class SavedGame(
    val levelIndex: Int,
    val state: GameState,
    val elapsedMs: Long,
    val savedAtMs: Long,
)
