package com.tm.meow.sudoku.puzzle.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsEvent
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsUserProps
import com.tm.meow.sudoku.puzzle.domain.model.Settings
import com.tm.meow.sudoku.puzzle.domain.usecase.GetAppLanguage
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveSettings
import com.tm.meow.sudoku.puzzle.domain.usecase.SetAppLanguage
import com.tm.meow.sudoku.puzzle.domain.usecase.SyncNotificationPermission
import com.tm.meow.sudoku.puzzle.domain.usecase.UpdateSettings
import com.tm.meow.sudoku.puzzle.ui.screens.game.SettingToggle
import com.tm.meow.sudoku.puzzle.ui.screens.game.SettingsUi
import com.tm.meow.sudoku.puzzle.ui.screens.home.withToggle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val syncPermission: SyncNotificationPermission,
    private val analytics: AnalyticsLogger,
    private val getAppLanguage: GetAppLanguage,
    private val setAppLanguage: SetAppLanguage,
) : ViewModel() {

    init {
        analytics.log(AnalyticsEvent.SettingsOpen("screen"))
    }

    val state: StateFlow<SettingsUi> =
        observeSettings().map { it.toUi() }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            Settings().toUi(),
        )

    private val _languageTag = MutableStateFlow(getAppLanguage().tag)

    /** BCP-47 tag of the language in effect (see AppLanguages.effective). Plain String so the dialog state can be saved. */
    val languageTag: StateFlow<String> = _languageTag.asStateFlow()

    /** Re-read the language (the system per-app language picker can change it while this ViewModel lives on). */
    fun refreshLanguage() {
        _languageTag.value = getAppLanguage().tag
    }

    /** Language dialog Confirm: apply + persist (platform recreates the activity). No-op when unchanged or unknown. */
    fun onLanguageConfirmed(tag: String) {
        if (tag == _languageTag.value) return
        val applied = try {
            setAppLanguage(tag)
        } catch (t: Throwable) {
            null // best effort: the picker simply keeps the old language
        } ?: return
        analytics.log(AnalyticsEvent.LanguageChange(applied.tag))
        analytics.setUserProperty(AnalyticsUserProps.APP_LANGUAGE, applied.tag)
        _languageTag.value = applied.tag
    }

    /**
     * Call on Settings resume and after a permission result. [granted] null = read the live OS state.
     * A not-granted -> granted transition turns all three reminders ON once; otherwise toggles are untouched.
     */
    fun syncNotificationPermission(granted: Boolean? = null) {
        viewModelScope.launch {
            try {
                if (granted == null) syncPermission() else syncPermission(granted)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // best effort
            }
        }
    }

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
    sudokuReminders = sudokuReminders,
    reduceEffects = reduceEffects,
)
