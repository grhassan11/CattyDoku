package com.threetech.catpuzzle.domain.repository

import com.threetech.catpuzzle.domain.model.Settings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<Settings>

    suspend fun update(transform: (Settings) -> Settings)
}
