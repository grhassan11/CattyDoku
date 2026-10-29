package com.threetech.catpuzzle.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threetech.catpuzzle.domain.analytics.AnalyticsEvent
import com.threetech.catpuzzle.domain.analytics.AnalyticsLogger
import com.threetech.catpuzzle.domain.model.Settings
import com.threetech.catpuzzle.domain.usecase.ObserveSettings
import com.threetech.catpuzzle.domain.usecase.UpdateSettings
import com.threetech.catpuzzle.ui.screens.game.SettingToggle
import com.threetech.catpuzzle.ui.screens.game.SettingsUi
import com.threetech.catpuzzle.ui.screens.home.withToggle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings screen state holder (opened from Home and from Game). Same use cases as before: [ObserveSettings] for
 * the live values, [UpdateSettings] for toggles. Game / Home view models observe the same flow, so a toggle here shows
 * up in the game the moment the user navigates back.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettings,
    private val updateSettings: UpdateSettings,
    private val analytics: AnalyticsLogger,
) : ViewModel() {

    val state: StateFlow<SettingsUi> =
        observeSettings().map { it.toUi() }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            Settings().toUi(),
        )

    fun onSettingChanged(toggle: SettingToggle, enabled: Boolean) {
        analytics.log(AnalyticsEvent.SettingChange(toggle.name.lowercase(), enabled))
        viewModelScope.launch {
            try {
                updateSettings { it.withToggle(toggle, enabled) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // best effort, same as the game and home screens
            }
        }
    }
}

private fun Settings.toUi() = SettingsUi(
    music = music,
    sfx = sfx,
    voice = voice,
    haptics = haptics,
    patternMode = patternMode,
    streakReminders = streakReminders,
    dailyReminders = dailyReminders,
)
