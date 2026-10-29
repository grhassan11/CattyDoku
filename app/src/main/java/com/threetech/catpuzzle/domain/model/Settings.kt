package com.threetech.catpuzzle.domain.model

/** User settings. Defaults match the reference screenshot. */
data class Settings(
    val music: Boolean = true,
    val sfx: Boolean = true,
    val voice: Boolean = true,
    val haptics: Boolean = true,
    val patternMode: Boolean = false,
    /** Evening "streak is about to break" notification. Default ON (still needs the OS permission). */
    val streakReminders: Boolean = true,
    /** Daily 20:00 "Daily Challenge" notification. Default ON (still needs the OS permission). */
    val dailyReminders: Boolean = true,
)

/**
 * One-time / bookkeeping flags.
 * [lastPlayedLevel]: 0 = none yet. [installDateEpochDay]: local date, null = not recorded yet.
 * [lastInterstitialLevel] / [sessionAdCount]: ad-gate counters (0 = none).
 */
data class AppFlags(
    val termsAccepted: Boolean = false,
    val tutorialDone: Boolean = false,
    val notificationPromptAsked: Boolean = false,
    val lastPlayedLevel: Int = 0,
    val installDateEpochDay: Long? = null,
    val lastInterstitialLevel: Int = 0,
    val sessionAdCount: Int = 0,
    /** Version of the Terms/Privacy the user accepted (0 = none). See TermsPolicy. */
    val termsVersion: Int = 0,
    /** Rate us dialog was shown (the automatic prompt is once only, see RateUsPolicy). */
    val rateUsShown: Boolean = false,
    /** Times the player closed the Rate us dialog without rating ("Not now", back, scrim). */
    val rateUsDismissedCount: Int = 0,
    /** Player gave 4-5 stars and was sent to the store review flow. */
    val rateUsRated: Boolean = false,
)
