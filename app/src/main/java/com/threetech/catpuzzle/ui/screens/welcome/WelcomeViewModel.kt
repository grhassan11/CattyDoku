package com.threetech.catpuzzle.ui.screens.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threetech.catpuzzle.domain.usecase.AcceptTerms
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface WelcomeEvent {
    /** Terms acceptance is saved: continue the start flow. */
    data object Accepted : WelcomeEvent
}

/** First-launch Welcome / Terms. The only way forward is [onAccept]. */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val acceptTerms: AcceptTerms,
) : ViewModel() {

    private val busy = AtomicBoolean(false)
    private val eventChannel = Channel<WelcomeEvent>(Channel.BUFFERED)
    val events: Flow<WelcomeEvent> = eventChannel.receiveAsFlow()

    /** Saves acceptance, then emits [WelcomeEvent.Accepted]. Double taps are ignored. */
    fun onAccept() {
        if (!busy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                acceptTerms()
                eventChannel.send(WelcomeEvent.Accepted)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                busy.set(false) // save failed: let the user tap again
            }
        }
    }
}
