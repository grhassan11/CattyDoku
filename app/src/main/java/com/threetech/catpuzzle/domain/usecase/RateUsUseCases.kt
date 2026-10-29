package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.rating.RateAction
import com.threetech.catpuzzle.domain.rating.RateUsPolicy
import com.threetech.catpuzzle.domain.repository.AppFlagsRepository
import com.threetech.catpuzzle.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.first

/**
 * Asks [RateUsPolicy] whether the Rate us dialog is due now. Reads highest won level, persisted flags and the in-memory
 * ad session. When due it ALSO records "shown" in the same call, so the dialog can never appear twice even if the app
 * is killed while it is open.
 */
class TryShowRateUs(
    private val progress: ProgressRepository,
    private val flags: AppFlagsRepository,
    private val tracker: AdSessionTracker,
) {
    suspend operator fun invoke(): Boolean {
        val current = flags.flags.first()
        val highest = progress.observeAll().first().maxOfOrNull { it.levelIndex } ?: 0
        val last = tracker.lastAdShownMs
        val due = RateUsPolicy.shouldPrompt(
            highestLevelWon = highest,
            flags = current,
            startFlowComplete = tracker.startFlowComplete,
            fullScreenAdShowing = tracker.fullScreenAdShowing,
            consentFormShowing = tracker.consentFormShowing,
            msSinceLastFullScreenAd = last?.let { tracker.now() - it },
        )
        if (due) flags.update { it.copy(rateUsShown = true) }
        return due
    }
}

/** "Not now" / back / scrim tap. */
class DismissRateUs(private val flags: AppFlagsRepository) {
    suspend operator fun invoke() {
        flags.update { it.copy(rateUsDismissedCount = it.rateUsDismissedCount + 1) }
    }
}

/** A star rating was submitted. 4-5 marks the app as rated; returns what the UI does next. */
class SubmitRateUs(private val flags: AppFlagsRepository) {
    suspend operator fun invoke(stars: Int): RateAction {
        val action = RateUsPolicy.actionFor(stars)
        if (action == RateAction.STORE_LISTING) flags.update { it.copy(rateUsRated = true) }
        return action
    }
}
