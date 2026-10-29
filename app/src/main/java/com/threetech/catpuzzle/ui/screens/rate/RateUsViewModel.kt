package com.threetech.catpuzzle.ui.screens.rate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threetech.catpuzzle.domain.analytics.AnalyticsEvent
import com.threetech.catpuzzle.domain.analytics.AnalyticsLogger
import com.threetech.catpuzzle.domain.analytics.NoopAnalyticsLogger
import com.threetech.catpuzzle.domain.rating.RateAction
import com.threetech.catpuzzle.domain.usecase.DismissRateUs
import com.threetech.catpuzzle.domain.usecase.SubmitRateUs
import com.threetech.catpuzzle.domain.usecase.TryShowRateUs
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
    /** 1-3 stars: show a "Rating Submitted" toast. */
    data object ShowSubmitted : RateUsEvent

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
            analytics.log(AnalyticsEvent.RatePrompt("shown", 0))
        }
        return due
    }

    /** Not now / back / scrim tap. */
    fun onDismiss() {
        if (!_visible.value) return
        _visible.value = false
        analytics.log(AnalyticsEvent.RatePrompt("dismissed", 0))
        viewModelScope.launch { bestEffort { dismissRateUs() } }
    }

    /** Submit with [stars] (1..5). */
    fun onSubmit(stars: Int) {
        if (!_visible.value) return
        val s = stars.coerceIn(1, 5)
        _visible.value = false
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
                    _events.trySend(RateUsEvent.ShowSubmitted)
                }
                RateAction.STORE_LISTING -> {
                    analytics.log(AnalyticsEvent.RatePrompt("store_listing", s))
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
