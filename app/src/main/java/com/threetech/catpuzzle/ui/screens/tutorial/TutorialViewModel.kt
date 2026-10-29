package com.threetech.catpuzzle.ui.screens.tutorial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threetech.catpuzzle.domain.analytics.AnalyticsEvent
import com.threetech.catpuzzle.domain.analytics.AnalyticsLogger
import com.threetech.catpuzzle.domain.analytics.NoopAnalyticsLogger
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.Settings
import com.threetech.catpuzzle.domain.tutorial.TutorialAction
import com.threetech.catpuzzle.domain.tutorial.TutorialEngine
import com.threetech.catpuzzle.domain.tutorial.TutorialOutcome
import com.threetech.catpuzzle.domain.tutorial.TutorialState
import com.threetech.catpuzzle.domain.usecase.CompleteTutorial
import com.threetech.catpuzzle.domain.usecase.ObserveSettings
import com.threetech.catpuzzle.ui.screens.game.HapticKind
import com.threetech.catpuzzle.ui.screens.game.SfxKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Drives the scripted tutorial. Everything is in memory: no autosave, no level progress, no streak,
 * no score, no inventory, no ads, no starter cat. The only write is the `tutorial_done` flag, set
 * on finish or skip.
 */
@HiltViewModel
class TutorialViewModel @Inject constructor(
    private val engine: TutorialEngine,
    observeSettings: ObserveSettings,
    private val completeTutorial: CompleteTutorial,
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
) : ViewModel() {

    // Main-thread only (UI callbacks and viewModelScope run on Main).
    private var tutorial = TutorialState()
    private var settings = Settings()
    private val eventChannel = Channel<TutorialUiEvent>(Channel.BUFFERED)
    private var finishing = false

    val events = eventChannel.receiveAsFlow()

    private val _state = MutableStateFlow(TutorialUiMapper.map(engine, tutorial, settings))
    val state: StateFlow<TutorialUiState> = _state.asStateFlow()

    init {
        analytics.log(AnalyticsEvent.TutorialBegin)
        viewModelScope.launch {
            observeSettings().collect {
                settings = it
                publish()
            }
        }
    }

    private fun publish() {
        _state.value = TutorialUiMapper.map(engine, tutorial, settings)
    }

    fun onCellTap(pos: Pos) = dispatch(TutorialAction.Tap(pos))

    fun onCellDoubleTap(pos: Pos) = dispatch(TutorialAction.DoubleTap(pos))

    fun onDrag(path: List<Pos>) = dispatch(TutorialAction.Drag(path))

    fun onHint() = dispatch(TutorialAction.UseHint)

    fun onCatBooster() = dispatch(TutorialAction.UseCatBooster)

    /** Next / Got it / Let's play. */
    fun onContinue() = dispatch(TutorialAction.Continue)

    fun onSkip() {
        if (finishing) return
        analytics.log(AnalyticsEvent.TutorialComplete(skipped = true))
        tutorial = engine.skip(tutorial).state
        publish()
        finish()
    }

    private fun dispatch(action: TutorialAction) {
        if (finishing) return
        val result = engine.apply(tutorial, action)
        tutorial = result.state
        publish()
        when (result.outcome) {
            TutorialOutcome.REJECTED -> {
                haptic(HapticKind.WRONG)
                sfx(SfxKind.BLOCKED)
            }
            TutorialOutcome.APPLIED, TutorialOutcome.ADVANCED -> {
                haptic(if (action is TutorialAction.DoubleTap || action is TutorialAction.UseCatBooster) HapticKind.PLACE else HapticKind.TICK)
                when (action) {
                    is TutorialAction.DoubleTap, TutorialAction.UseCatBooster -> sfx(SfxKind.MEOW)
                    TutorialAction.UseHint -> sfx(SfxKind.HINT)
                    else -> Unit
                }
            }
            TutorialOutcome.COMPLETED -> {
                analytics.log(AnalyticsEvent.TutorialComplete(skipped = false))
                finish()
            }
            TutorialOutcome.NEEDS_SECOND_TAP, TutorialOutcome.IGNORED -> Unit
        }
    }

    private fun haptic(kind: HapticKind) {
        if (settings.haptics) eventChannel.trySend(TutorialUiEvent.Haptic(kind))
    }

    private fun sfx(kind: SfxKind) {
        if (settings.sfx) eventChannel.trySend(TutorialUiEvent.Sfx(kind))
    }

    private fun finish() {
        if (finishing) return
        finishing = true
        viewModelScope.launch {
            try {
                completeTutorial()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Flag not saved: tutorial shows again next launch. Still let the player through.
            }
            eventChannel.send(TutorialUiEvent.Finished)
        }
    }
}
