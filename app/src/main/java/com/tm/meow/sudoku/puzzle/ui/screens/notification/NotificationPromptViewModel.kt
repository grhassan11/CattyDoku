package com.tm.meow.sudoku.puzzle.ui.screens.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsEvent
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.NoopAnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.usecase.DisableReminders
import com.tm.meow.sudoku.puzzle.domain.usecase.MarkNotificationPromptAsked
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface NotificationPromptEvent {
    /** The prompt is answered and remembered: continue the start flow. */
    data object Finished : NotificationPromptEvent
}

/**
 * Notification pre-prompt. The system permission request itself lives in the Route (needs an Activity
 * result launcher); this only remembers "asked" so the prompt shows once, whatever the answer.
 */
@HiltViewModel
class NotificationPromptViewModel @Inject constructor(
    private val markAsked: MarkNotificationPromptAsked,
    private val disableReminders: DisableReminders,
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
) : ViewModel() {

    private val busy = AtomicBoolean(false)
    private val eventChannel = Channel<NotificationPromptEvent>(Channel.BUFFERED)
    val events: Flow<NotificationPromptEvent> = eventChannel.receiveAsFlow()

    /** Call after the system dialog returns ([granted] = its result) and for "Not now" (false). Idempotent. */
    fun onAnswered(granted: Boolean = false) {
        if (!busy.compareAndSet(false, true)) return
        analytics.log(AnalyticsEvent.NotificationPermission(granted))
        viewModelScope.launch {
            if (!granted) {
                try {
                    disableReminders()
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    // best effort: Settings still shows them OFF while permission is missing
                }
            }
            try {
                markAsked()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // not saved: prompt may show once more next launch; never block the player
            }
            eventChannel.send(NotificationPromptEvent.Finished)
        }
    }
}
