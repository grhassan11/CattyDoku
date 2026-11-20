package com.tm.meow.sudoku.puzzle.ui.screens.rate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsEvent
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.NoopAnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.rating.RateAction
import com.tm.meow.sudoku.puzzle.domain.usecase.DismissRateUs
import com.tm.meow.sudoku.puzzle.domain.usecase.ExternalLaunchMarker
import com.tm.meow.sudoku.puzzle.domain.usecase.SubmitRateUs
import com.tm.meow.sudoku.puzzle.domain.usecase.TryShowRateUs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** One-shot follow-ups the route performs (needs a Context, so not done here). */
sealed interface RateUsEvent {
    /** 1-3 stars: open the user's email app with the feedback draft; [stars] is appended to the subject ("1★"). */
    data class OpenFeedbackEmail(val stars: Int) : RateUsEvent

    /** 4-5 stars: open the Play Store listing. */
    data object OpenStoreListing : RateUsEvent
}

/**
 * Rate us dialog state holder, shared by Home and Game routes (one instance per nav entry). All policy lives in
 * domain ([TryShowRateUs]); store listing / toast are one-shot events performed by the UI.
 */
@HiltViewModel
class RateUsViewModel @Inject constructor(
    private val tryShowRateUs: TryShowRateUs,
    private val dismissRateUs: DismissRateUs,
    private val submitRateUs: SubmitRateUs,
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
    private val externalLaunch: ExternalLaunchMarker = ExternalLaunchMarker.Noop,
) : ViewModel() {

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private val _events = Channel<RateUsEvent>(Channel.BUFFERED)
    val events: Flow<RateUsEvent> = _events.receiveAsFlow()

    /**
     * Call at a safe boundary (Home shown, or the user tapped "next level"). Shows the dialog when the policy says it is
     * due and returns true; false = nothing shown (caller carries on). Failures read as "not due".
     */
    suspend fun tryShow(): Boolean {
        if (_visible.value) return true
        val due = try {
            tryShowRateUs()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            false
        }
        if (due) {
            _visible.value = true
            analytics.log(AnalyticsEvent.RatePrompt("shown"))
        }
        return due
    }

    /** True while the visible dialog was opened by the user (Settings), not by the auto policy. */
    private var manual = false

    /**
     * User-initiated open (Settings "Rate Us"). Skips [TryShowRateUs]: ignores the once-only / rated flags and does NOT
     * write "shown". Dismissing it does not bump the auto-prompt dismissed counter.
     */
    fun showManually() {
        if (_visible.value) return
        manual = true
        _visible.value = true
        analytics.log(AnalyticsEvent.RatePrompt("shown_manual"))
    }

    /** Close X / back / scrim tap. Auto-prompt bumps the dismissed counter; manual (Settings) does not. */
    fun onDismiss() {
        if (!_visible.value) return
        _visible.value = false
        analytics.log(AnalyticsEvent.RatePrompt("dismissed"))
        if (manual) {
            manual = false
            return
        }
        viewModelScope.launch { bestEffort { dismissRateUs() } }
    }

    /** A star was tapped ([stars] 1..5): closes the dialog and emits the follow-up event (store / feedback email). */
    fun onSubmit(stars: Int) {
        if (!_visible.value) return
        _visible.value = false
        manual = false
        submit(stars)
    }

    /**
     * Star tapped inside the Home exit sheet. Same routing/events as [onSubmit] but there is no dialog to close, the
     * auto-prompt flags/counters are untouched (only 4-5 stars marks rated, via [SubmitRateUs]).
     */
    fun onExitSheetStar(stars: Int) = submit(stars)

    private fun submit(stars: Int) {
        val s = stars.coerceIn(1, 5)
        viewModelScope.launch {
            val action = try {
                submitRateUs(s)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (s >= 4) RateAction.STORE_LISTING else RateAction.SUBMITTED_LOW
            }
            when (action) {
                RateAction.SUBMITTED_LOW -> {
                    analytics.log(AnalyticsEvent.RatePrompt("submitted_low", s))
                    externalLaunch.mark() // email app opens next: its return may show an app-open ad
                    _events.trySend(RateUsEvent.OpenFeedbackEmail(s))
                }
                RateAction.STORE_LISTING -> {
                    analytics.log(AnalyticsEvent.RatePrompt("store_listing", s))
                    externalLaunch.mark() // Play Store opens next: its return may show an app-open ad
                    _events.trySend(RateUsEvent.OpenStoreListing)
                }
            }
        }
    }

    private suspend inline fun bestEffort(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // persistence hiccup must not break the screen
        }
    }
}
