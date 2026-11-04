package com.tm.meow.sudoku.puzzle.domain.repository

import com.tm.meow.sudoku.puzzle.domain.model.Settings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<Settings>

    suspend fun update(transform: (Settings) -> Settings)
}
