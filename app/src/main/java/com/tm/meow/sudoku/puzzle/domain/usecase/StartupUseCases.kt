package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.startup.SdkLevelProvider
import com.tm.meow.sudoku.puzzle.domain.startup.StartInputs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** One-shot snapshot that drives the start flow (see StartFlow). */
class ReadStartInputs(
    private val flags: AppFlagsRepository,
) {
    suspend operator fun invoke(): StartInputs = StartInputs(tutorialDone = flags.flags.first().tutorialDone)

    /** First-launch defaults, used when the flags store cannot be read (corrupt prefs / IO failure). */
    fun fallback(): StartInputs = StartInputs(tutorialDone = false)
}

/** True once the first-run tutorial is done: a returning launch (short splash minimum). */
class ObserveReturningLaunch(private val flags: AppFlagsRepository) {
    operator fun invoke(): Flow<Boolean> = flags.flags.map { it.tutorialDone }.distinctUntilChanged()
}

/** Remember the notification permission was asked (allowed, denied or skipped): it is asked once. Idempotent. */
class MarkNotificationPromptAsked(private val flags: AppFlagsRepository) {
    suspend operator fun invoke() {
        flags.update { it.copy(notificationPromptAsked = true) }
    }
}

/**
 * First Home entry: should the system POST_NOTIFICATIONS dialog be shown now? True only on API 33+ and only while
 * the "asked" flag is unset. When true it first WAITS (suspends) until neither the UMP consent form nor a full-screen
 * ad is on screen, so the dialog never stacks on top of them. Cancel the caller to abandon the wait.
 */
class ShouldAskNotificationPermission(
    private val flags: AppFlagsRepository,
    private val sdk: SdkLevelProvider,
    private val tracker: AdSessionTracker,
) {
    suspend operator fun invoke(): Boolean {
        if (!shouldAsk(sdk.sdkInt(), flags.flags.first().notificationPromptAsked)) return false
        while (tracker.consentFormShowing || tracker.fullScreenAdShowing) delay(POLL_MS)
        // flag may have flipped while waiting (another entry asked first)
        return shouldAsk(sdk.sdkInt(), flags.flags.first().notificationPromptAsked)
    }

    companion object {
        /** POST_NOTIFICATIONS is a runtime permission from API 33 (Android 13). */
        const val NOTIFICATION_MIN_SDK = 33
        const val POLL_MS = 250L

        fun shouldAsk(sdkInt: Int, alreadyAsked: Boolean): Boolean = sdkInt >= NOTIFICATION_MIN_SDK && !alreadyAsked
    }
}
