package com.threetech.catpuzzle.domain.rating

import com.threetech.catpuzzle.domain.model.AppFlags

/** What a star rating leads to. */
enum class RateAction {
    /** 4-5 stars: open the Play Store listing so the player rates there. */
    STORE_LISTING,

    /** 1-3 stars: nothing opens; the UI just confirms "Rating Submitted". */
    SUBMITTED_LOW,
}

/**
 * The ONE rule for the "Rate us" prompt. Pure, no Android, no clock.
 *
 * Shown at most ONCE ever (automatically): once [AppFlags.rateUsShown] or [AppFlags.rateUsRated] is set it never comes
 * back (re-ask policy: once only). Eligible after the player has WON level [AFTER_LEVEL]; only at a boundary the caller
 * picks (Home, or the user-initiated "next level" tap), never mid-play, never in the tutorial or the daily.
 * Never while a full-screen ad / the consent form is on screen or right after one ([MIN_GAP_AFTER_AD_MS]).
 */
object RateUsPolicy {
    /** Prompt becomes eligible once this level has been won. */
    const val AFTER_LEVEL = 5

    /** Quiet period after any full-screen ad (interstitial / app-open / rewarded). */
    const val MIN_GAP_AFTER_AD_MS = 120_000L

    const val MAX_STARS = 5

    /** 4 and 5 stars go to the store listing. */
    const val STORE_MIN_STARS = 4

    fun shouldPrompt(
        highestLevelWon: Int,
        flags: AppFlags,
        startFlowComplete: Boolean,
        fullScreenAdShowing: Boolean,
        consentFormShowing: Boolean,
        /** Millis since the last full-screen ad was shown; null = none this process. */
        msSinceLastFullScreenAd: Long?,
    ): Boolean = when {
        flags.rateUsShown || flags.rateUsRated -> false
        highestLevelWon < AFTER_LEVEL -> false
        !startFlowComplete -> false
        fullScreenAdShowing || consentFormShowing -> false
        msSinceLastFullScreenAd != null && msSinceLastFullScreenAd < MIN_GAP_AFTER_AD_MS -> false
        else -> true
    }

    fun actionFor(stars: Int): RateAction =
        if (stars.coerceIn(1, MAX_STARS) >= STORE_MIN_STARS) RateAction.STORE_LISTING else RateAction.SUBMITTED_LOW
}
