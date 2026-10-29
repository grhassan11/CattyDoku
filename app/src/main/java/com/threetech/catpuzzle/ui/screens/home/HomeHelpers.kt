package com.threetech.catpuzzle.ui.screens.home

import com.threetech.catpuzzle.domain.model.Settings
import com.threetech.catpuzzle.ui.screens.game.SettingToggle

/** Alpha of the faint home background pattern (6-8% per spec). */
const val HOME_PATTERN_ALPHA = 0.07f

/** Whether the background tile at ([row], [col]) carries an X mark (others are plain rounded squares). Deterministic. */
fun patternHasX(row: Int, col: Int): Boolean = Math.floorMod(row * 3 + col * 5 + row * col, 7) == 2

/** Settings after flipping [toggle] to [enabled]. */
fun Settings.withToggle(toggle: SettingToggle, enabled: Boolean): Settings = when (toggle) {
    SettingToggle.MUSIC -> copy(music = enabled)
    SettingToggle.SFX -> copy(sfx = enabled)
    SettingToggle.VOICE -> copy(voice = enabled)
    SettingToggle.HAPTICS -> copy(haptics = enabled)
    SettingToggle.PATTERN_MODE -> copy(patternMode = enabled)
    SettingToggle.STREAK_REMINDERS -> copy(streakReminders = enabled)
    SettingToggle.DAILY_REMINDERS -> copy(dailyReminders = enabled)
}
