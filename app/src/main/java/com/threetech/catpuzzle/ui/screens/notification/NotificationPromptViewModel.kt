package com.threetech.catpuzzle.ui.screens.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threetech.catpuzzle.domain.usecase.MarkNotificationPromptAsked
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
) : ViewModel() {

    private val busy = AtomicBoolean(false)
    private val eventChannel = Channel<NotificationPromptEvent>(Channel.BUFFERED)
    val events: Flow<NotificationPromptEvent> = eventChannel.receiveAsFlow()

    /** Call after the system dialog returns (granted or not) and for "Not now". Idempotent. */
    fun onAnswered() {
        if (!busy.compareAndSet(false, true)) return
        viewModelScope.launch {
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
