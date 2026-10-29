package com.threetech.catpuzzle.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.threetech.catpuzzle.data.local.prefs.ioSafe
import com.threetech.catpuzzle.data.local.prefs.toAppFlags
import com.threetech.catpuzzle.data.local.prefs.toSettings
import com.threetech.catpuzzle.data.local.prefs.write
import com.threetech.catpuzzle.domain.model.AppFlags
import com.threetech.catpuzzle.domain.model.Settings
import com.threetech.catpuzzle.domain.repository.AppFlagsRepository
import com.threetech.catpuzzle.domain.repository.SettingsRepository
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
