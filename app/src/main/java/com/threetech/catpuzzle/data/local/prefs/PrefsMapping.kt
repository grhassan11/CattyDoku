package com.threetech.catpuzzle.data.local.prefs

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.threetech.catpuzzle.domain.billing.Entitlement
import com.threetech.catpuzzle.domain.model.AppFlags
import com.threetech.catpuzzle.domain.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

/**
 * Preference key names are persisted: never rename. Defaults come from the domain models so there is
 * one source of truth.
 */
internal object PrefKeys {
    val MUSIC = booleanPreferencesKey("music")
    val SFX = booleanPreferencesKey("sfx")
    val VOICE = booleanPreferencesKey("voice")
    val HAPTICS = booleanPreferencesKey("haptics")
    val PATTERN_MODE = booleanPreferencesKey("pattern_mode")
    val STREAK_REMINDERS = booleanPreferencesKey("streak_reminders")
    val DAILY_REMINDERS = booleanPreferencesKey("daily_reminders")

    val TERMS_ACCEPTED = booleanPreferencesKey("terms_accepted")
    val TERMS_VERSION = intPreferencesKey("terms_version")
    val TUTORIAL_DONE = booleanPreferencesKey("tutorial_done")
    val NOTIFICATION_PROMPT_ASKED = booleanPreferencesKey("notification_prompt_asked")
    val LAST_PLAYED_LEVEL = intPreferencesKey("last_played_level")
    val INSTALL_DATE_EPOCH_DAY = longPreferencesKey("install_date_epoch_day")
    val LAST_INTERSTITIAL_LEVEL = intPreferencesKey("last_interstitial_level")
    val SESSION_AD_COUNT = intPreferencesKey("session_ad_count")
    val RATE_US_SHOWN = booleanPreferencesKey("rate_us_shown")
    val RATE_US_DISMISSED_COUNT = intPreferencesKey("rate_us_dismissed_count")
    val RATE_US_RATED = booleanPreferencesKey("rate_us_rated")

    // Ads-removal entitlement CACHE (source of truth = Play query). See EntitlementPolicy for expiry.
    val ADS_REMOVED_MONTHLY = booleanPreferencesKey("ads_removed_monthly")
    val ADS_REMOVED_FOREVER = booleanPreferencesKey("ads_removed_forever")
    val ADS_REMOVED_VERIFIED_AT = longPreferencesKey("ads_removed_verified_at")
}

/** A corrupt/unreadable store reads as defaults instead of crashing. */
internal fun Flow<Preferences>.ioSafe(): Flow<Preferences> =
    catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

internal fun Preferences.toSettings(): Settings {
    val d = Settings()
    return Settings(
        music = this[PrefKeys.MUSIC] ?: d.music,
        sfx = this[PrefKeys.SFX] ?: d.sfx,
        voice = this[PrefKeys.VOICE] ?: d.voice,
        haptics = this[PrefKeys.HAPTICS] ?: d.haptics,
        patternMode = this[PrefKeys.PATTERN_MODE] ?: d.patternMode,
        streakReminders = this[PrefKeys.STREAK_REMINDERS] ?: d.streakReminders,
        dailyReminders = this[PrefKeys.DAILY_REMINDERS] ?: d.dailyReminders,
    )
}

internal fun MutablePreferences.write(s: Settings) {
    this[PrefKeys.MUSIC] = s.music
    this[PrefKeys.SFX] = s.sfx
    this[PrefKeys.VOICE] = s.voice
    this[PrefKeys.HAPTICS] = s.haptics
    this[PrefKeys.PATTERN_MODE] = s.patternMode
    this[PrefKeys.STREAK_REMINDERS] = s.streakReminders
    this[PrefKeys.DAILY_REMINDERS] = s.dailyReminders
}

internal fun Preferences.toAppFlags(): AppFlags {
    val d = AppFlags()
    return AppFlags(
        termsAccepted = this[PrefKeys.TERMS_ACCEPTED] ?: d.termsAccepted,
        tutorialDone = this[PrefKeys.TUTORIAL_DONE] ?: d.tutorialDone,
        notificationPromptAsked = this[PrefKeys.NOTIFICATION_PROMPT_ASKED] ?: d.notificationPromptAsked,
        lastPlayedLevel = this[PrefKeys.LAST_PLAYED_LEVEL] ?: d.lastPlayedLevel,
        installDateEpochDay = this[PrefKeys.INSTALL_DATE_EPOCH_DAY],
        lastInterstitialLevel = this[PrefKeys.LAST_INTERSTITIAL_LEVEL] ?: d.lastInterstitialLevel,
        sessionAdCount = this[PrefKeys.SESSION_AD_COUNT] ?: d.sessionAdCount,
        termsVersion = this[PrefKeys.TERMS_VERSION] ?: d.termsVersion,
        rateUsShown = this[PrefKeys.RATE_US_SHOWN] ?: d.rateUsShown,
        rateUsDismissedCount = this[PrefKeys.RATE_US_DISMISSED_COUNT] ?: d.rateUsDismissedCount,
        rateUsRated = this[PrefKeys.RATE_US_RATED] ?: d.rateUsRated,
    )
}

internal fun MutablePreferences.write(f: AppFlags) {
    this[PrefKeys.TERMS_ACCEPTED] = f.termsAccepted
    this[PrefKeys.TUTORIAL_DONE] = f.tutorialDone
    this[PrefKeys.NOTIFICATION_PROMPT_ASKED] = f.notificationPromptAsked
    this[PrefKeys.LAST_PLAYED_LEVEL] = f.lastPlayedLevel
    val install = f.installDateEpochDay
    if (install != null) this[PrefKeys.INSTALL_DATE_EPOCH_DAY] = install else remove(PrefKeys.INSTALL_DATE_EPOCH_DAY)
    this[PrefKeys.LAST_INTERSTITIAL_LEVEL] = f.lastInterstitialLevel
    this[PrefKeys.SESSION_AD_COUNT] = f.sessionAdCount
    this[PrefKeys.TERMS_VERSION] = f.termsVersion
    this[PrefKeys.RATE_US_SHOWN] = f.rateUsShown
    this[PrefKeys.RATE_US_DISMISSED_COUNT] = f.rateUsDismissedCount
    this[PrefKeys.RATE_US_RATED] = f.rateUsRated
}

internal fun Preferences.toEntitlement(): Entitlement = Entitlement(
    monthlyActive = this[PrefKeys.ADS_REMOVED_MONTHLY] ?: false,
    foreverOwned = this[PrefKeys.ADS_REMOVED_FOREVER] ?: false,
    verifiedAtMs = this[PrefKeys.ADS_REMOVED_VERIFIED_AT] ?: 0L,
)

internal fun MutablePreferences.write(e: Entitlement) {
    this[PrefKeys.ADS_REMOVED_MONTHLY] = e.monthlyActive
    this[PrefKeys.ADS_REMOVED_FOREVER] = e.foreverOwned
    this[PrefKeys.ADS_REMOVED_VERIFIED_AT] = e.verifiedAtMs
}
