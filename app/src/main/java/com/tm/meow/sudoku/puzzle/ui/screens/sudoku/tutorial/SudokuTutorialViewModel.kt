package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.tutorial

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsEvent
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.NoopAnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuSize
import com.tm.meow.sudoku.puzzle.domain.sudoku.tutorial.SudokuTutorialEngine
import com.tm.meow.sudoku.puzzle.domain.sudoku.tutorial.SudokuTutorialPhase
import com.tm.meow.sudoku.puzzle.domain.sudoku.tutorial.SudokuTutorialScript
import com.tm.meow.sudoku.puzzle.domain.sudoku.tutorial.SudokuTutorialState
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.CompleteSudokuTutorial
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.IsSudokuTutorialDone
import com.tm.meow.sudoku.puzzle.ui.navigation.Routes
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

/** One-shot: leave the tutorial (the done flag is already saved). */
sealed interface SudokuTutorialEvent {
    data object Finished : SudokuTutorialEvent
}

/**
 * Drives the scripted Sudoku tutorial of one board size (nav arg `size` = SudokuSize.name, 9x9 when missing). Everything is
 * in memory (restart = step 1): no game, save, score, streak, hint stock, ads or progress is touched. The only write is
 * that size's seen flag, set on finish, skip or back.
 */
@HiltViewModel
class SudokuTutorialViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val completeTutorial: CompleteSudokuTutorial,
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
) : ViewModel() {

    private val size: SudokuSize = savedState.get<String>(Routes.ARG_SUDOKU_TUTORIAL_SIZE)
        ?.let { id -> SudokuSize.entries.firstOrNull { it.name == id } }
        ?: SudokuSize.S9
    private val sizeLabel = "${size.n}x${size.n}"

    // Main-thread only (UI callbacks and viewModelScope run on Main).
    private var tutorial: SudokuTutorialState = SudokuTutorialEngine.initial(size)
    private var finishing = false
    private val eventChannel = Channel<SudokuTutorialEvent>(Channel.BUFFERED)
    val events: Flow<SudokuTutorialEvent> = eventChannel.receiveAsFlow()

    private val _state = MutableStateFlow(SudokuTutorialUiMapper.map(tutorial))
    val state: StateFlow<SudokuTutorialUiState> = _state.asStateFlow()

    init {
        analytics.log(AnalyticsEvent.SudokuTutorialBegin(sizeLabel))
    }

    fun onStartLesson() = apply(SudokuTutorialEngine.start(tutorial))

    fun onCellTap(cell: Int) = apply(SudokuTutorialEngine.tapCell(tutorial, cell))

    fun onDigit(digit: Int) = apply(SudokuTutorialEngine.enterDigit(tutorial, digit))

    /** "Next: Solving Tricks". Past the last lesson it behaves like finish. */
    fun onNextLesson() {
        val next = SudokuTutorialEngine.nextLesson(tutorial)
        if (next == null) onExit() else apply(next)
    }

    /** Finish, skip, back and system back all end here: save the flag once, then leave. */
    fun onExit() {
        if (finishing) return
        finishing = true
        val completed = tutorial.lesson >= SudokuTutorialScript.LESSON_COUNT &&
            tutorial.phase == SudokuTutorialPhase.LESSON_COMPLETE
        analytics.log(AnalyticsEvent.SudokuTutorialEnd(completed, sizeLabel))
        viewModelScope.launch {
            try {
                completeTutorial(size)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // Flag not saved: the gate may show the tutorial once more. Never block leaving.
            }
            eventChannel.send(SudokuTutorialEvent.Finished)
        }
    }

    private fun apply(next: SudokuTutorialState) {
        if (next == tutorial) return
        if (next.lesson != tutorial.lesson || next.step != tutorial.step) {
            analytics.log(AnalyticsEvent.SudokuTutorialStep(next.lesson, next.step, sizeLabel))
        }
        tutorial = next
        _state.value = SudokuTutorialUiMapper.map(next)
    }
}

/** First-play gate for the Sudoku entry screen: should the tutorial of a board size open before its first game? */
@HiltViewModel
class SudokuTutorialGateViewModel @Inject constructor(
    private val isDone: IsSudokuTutorialDone,
) : ViewModel() {

    /** True = that size's flag is not set yet. A read failure counts as done (never trap the player in the tutorial). */
    suspend fun shouldShow(size: SudokuSize): Boolean = try {
        !isDone(size)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        false
    }
}
