package com.tm.meow.sudoku.puzzle.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.tm.meow.sudoku.puzzle.data.local.prefs.ioSafe
import com.tm.meow.sudoku.puzzle.data.local.prefs.toAppFlags
import com.tm.meow.sudoku.puzzle.data.local.prefs.toSettings
import com.tm.meow.sudoku.puzzle.data.local.prefs.write
import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import com.tm.meow.sudoku.puzzle.domain.model.Settings
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<Settings> =
        dataStore.data.ioSafe().map { it.toSettings() }.distinctUntilChanged()

    override suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }
}

@Singleton
class AppFlagsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppFlagsRepository {
    override val flags: Flow<AppFlags> =
        dataStore.data.ioSafe().map { it.toAppFlags() }.distinctUntilChanged()

    override suspend fun update(transform: (AppFlags) -> AppFlags) {
        dataStore.edit { prefs -> prefs.write(transform(prefs.toAppFlags())) }
    }

    override suspend fun setInstallDateIfAbsent(epochDay: Long) {
        dataStore.edit { prefs ->
            val current = prefs.toAppFlags()
            if (current.installDateEpochDay == null) prefs.write(current.copy(installDateEpochDay = epochDay))
        }
    }
}
