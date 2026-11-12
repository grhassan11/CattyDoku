package com.tm.meow.sudoku.puzzle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsEvent
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.NoopAnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.usecase.ApplyNotificationPermission
import com.tm.meow.sudoku.puzzle.domain.usecase.MarkNotificationPromptAsked
import com.tm.meow.sudoku.puzzle.domain.usecase.ShouldAskNotificationPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * First-Home-entry POST_NOTIFICATIONS request. The system launcher lives in HomeRoute (needs an Activity result
 * registry); this decides WHEN to ask (API 33+, once, never over the UMP form / a full-screen ad) and applies the answer.
 */
@HiltViewModel
class NotificationAskViewModel @Inject constructor(
    private val shouldAsk: ShouldAskNotificationPermission,
    private val markAsked: MarkNotificationPromptAsked,
    private val applyPermission: ApplyNotificationPermission,
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
) : ViewModel() {

    /**
     * Suspends while a consent form / full-screen ad is up. True = caller must launch the system dialog now. The "asked"
     * flag is saved BEFORE returning true, so the dialog is never shown twice (not after denial, not after process death).
     */
    suspend fun claimAsk(): Boolean = try {
        if (shouldAsk()) {
            markAsked()
            true
        } else {
            false
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        false // never block Home; the flag is unsaved so the next Home entry retries
    }

    /** System dialog result. Granted = all reminders ON (observers book the work); denied = all OFF. Best effort. */
    fun onResult(granted: Boolean) {
        analytics.log(AnalyticsEvent.NotificationPermission(granted))
        viewModelScope.launch {
            try {
                applyPermission(granted)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // Settings still shows reminders OFF while permission is missing
            }
        }
    }
}
