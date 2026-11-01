package com.threetech.catpuzzle.ui.screens.sudoku.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.sudoku.engine.SudokuGameEngine
import com.threetech.catpuzzle.domain.sudoku.model.SudokuGameState
import com.threetech.catpuzzle.domain.sudoku.model.SudokuStatus
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTier
import com.threetech.catpuzzle.domain.sudoku.usecase.CompleteSudoku
import com.threetech.catpuzzle.domain.sudoku.usecase.SaveSudokuGame
import com.threetech.catpuzzle.domain.sudoku.usecase.ShowSudokuInterstitialIfAllowed
import com.threetech.catpuzzle.domain.sudoku.usecase.StartOrResumeSudoku
import com.threetech.catpuzzle.domain.sudoku.usecase.SudokuCompletion
import com.threetech.catpuzzle.domain.sudoku.usecase.SudokuHintResult
import com.threetech.catpuzzle.domain.sudoku.usecase.SudokuStartResult
import com.threetech.catpuzzle.domain.sudoku.usecase.UseSudokuHint
import com.threetech.catpuzzle.domain.time.TimeProvider
import com.threetech.catpuzzle.domain.usecase.ObserveBoosterBalances
import com.threetech.catpuzzle.ui.navigation.Routes
import com.threetech.catpuzzle.ui.screens.sudoku.entry.SudokuEntryUiMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Sudoku game. Owns the [SudokuGameState] (pure reducer: [SudokuGameEngine]) and the clock.
 *
 * Clock: monotonic ([TimeProvider.monotonicMillis]); ticks once a second while nothing holds it. Holds = user pause,
 * app stopped (ON_PAUSE), an open Restart / Help / hint dialog, leaving. Every hold pauses the engine state; the pause
 * overlay (board hidden) shows only for the user pause.
 *
 * Save: debounced after each change, at once on app stop / user pause / leave. A finished game is completed exactly once
 * ([completed] guard, serialized with saves by [ioLock] so a late save can never resurrect a cleared game).
 * The tier comes from [SavedStateHandle] (nav arg). The route arg `resume` is not needed: [StartOrResumeSudoku] resumes
 * a save when there is one and generates otherwise (the entry clears the save first for NEW GAME).
 */
@HiltViewModel
class SudokuGameViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val startOrResume: StartOrResumeSudoku,
    private val saveGame: SaveSudokuGame,
    private val useHint: UseSudokuHint,
    private val completeSudoku: CompleteSudoku,
    private val showInterstitial: ShowSudokuInterstitialIfAllowed,
    observeBoosters: ObserveBoosterBalances,
    private val time: TimeProvider,
) : ViewModel() {

    private val tier: SudokuTier? = savedState.get<String>(Routes.ARG_SUDOKU_TIER)
        ?.let { id -> SudokuTier.entries.firstOrNull { it.name == id } }

    private var game: SudokuGameState? = null
    private var hintCharges = 0
    private var dialog: SudokuDialogUi = SudokuDialogUi.None
    private var userPaused = false
    private var appStopped = false
    private var leaving = false
    private var completed = false
    private var hinting = false
    private var winBusy = false

    private var clockJob: Job? = null
    private var clockOrigin = 0L
    private var clockBase = 0L
    private var saveJob: Job? = null
    private val ioLock = Mutex()

    private val _state = MutableStateFlow(SudokuGameUiState(tier = tier ?: SudokuTier.EASY))
    val state: StateFlow<SudokuGameUiState> = _state.asStateFlow()

    private val _timeText = MutableStateFlow(SudokuEntryUiMapper.formatTime(0L))

    /** "mm:ss" of the running clock. Separate from [state] so the board does not rebuild every second. */
    val timeText: StateFlow<String> = _timeText.asStateFlow()

    private val eventChannel = Channel<SudokuGameEvent>(Channel.BUFFERED)
    val events: Flow<SudokuGameEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeBoosters().collect { stock ->
                hintCharges = stock[Booster.HINT] ?: 0
                render()
            }
        }
        viewModelScope.launch { loadGame() }
    }

    // ------------------------------------------------------------------ load

    private suspend fun loadGame() {
        val t = tier
        if (t == null) {
            exit(SudokuExitReason.FAILED)
            return
        }
        val result = try {
            startOrResume(t)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            SudokuStartResult.Failed(e.message.orEmpty())
        }
        when (result) {
            is SudokuStartResult.Locked -> exit(SudokuExitReason.LOCKED)
            is SudokuStartResult.Failed -> exit(SudokuExitReason.FAILED)
            is SudokuStartResult.Resumed -> begin(result.state)
            is SudokuStartResult.Started -> begin(result.state)
        }
    }

    private fun begin(state: SudokuGameState) {
        stopClock()
        completed = false
        userPaused = false
        dialog = SudokuDialogUi.None
        game = state.copy(paused = false)
        _timeText.value = SudokuEntryUiMapper.formatTime(state.elapsedMs)
        if (state.status == SudokuStatus.PLAYING) {
            applyHolds()
        } else {
            finish(state) // healed restore: already solved / out of hearts
            render()
        }
    }

    // ------------------------------------------------------------------ player actions

    fun onCellTap(cell: Int) = act(save = false) { g, _ -> SudokuGameEngine.select(g, cell) }

    fun onDigit(digit: Int) = act { g, el -> SudokuGameEngine.enterDigit(g, digit, el) }

    fun onUndo() = act { g, _ -> SudokuGameEngine.undo(g) }

    fun onErase() = act { g, _ -> SudokuGameEngine.erase(g) }

    fun onToggleNotes() = act(save = false) { g, _ -> SudokuGameEngine.toggleNotesMode(g) }

    fun onHint() {
        val g = game ?: return
        if (!g.isActive || hinting) return
        hinting = true
        viewModelScope.launch {
            try {
                when (val r = useHint(g, currentElapsed())) {
                    // Charge is spent: apply to the LATEST state (the player may have moved while it was spent).
                    is SudokuHintResult.Granted -> act { latest, el -> SudokuGameEngine.applyHint(latest, r.hint, el) }
                    SudokuHintResult.NoCharge -> eventChannel.send(SudokuGameEvent.NoHintCharge)
                    SudokuHintResult.Unavailable -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // charge consumption failed: nothing applied
            } finally {
                hinting = false
            }
        }
    }

    private fun act(save: Boolean = true, transform: (SudokuGameState, Long) -> SudokuGameState) {
        val g = game ?: return
        val next = transform(g, currentElapsed())
        if (next === g) return
        commit(next, save)
    }

    private fun commit(next: SudokuGameState, save: Boolean = true) {
        game = next
        _timeText.value = SudokuEntryUiMapper.formatTime(next.elapsedMs)
        if (next.status == SudokuStatus.PLAYING) {
            if (save) scheduleSave()
        } else {
            stopClock()
            finish(next)
        }
        render()
    }

    // ------------------------------------------------------------------ pause / lifecycle

    fun onPauseToggle() {
        val g = game ?: return
        if (g.status != SudokuStatus.PLAYING || dialog != SudokuDialogUi.None) return
        userPaused = !userPaused
        applyHolds()
        if (userPaused) flushSave()
    }

    /** ON_PAUSE (also fires for full-screen ads and the Shop): hold the clock and save now. */
    fun onAppStopped() {
        appStopped = true
        applyHolds()
        flushSave()
    }

    /** ON_RESUME. */
    fun onAppStarted() {
        appStopped = false
        applyHolds()
    }

    private fun holdActive(): Boolean = userPaused || appStopped || leaving || dialog.pausesClock

    private fun applyHolds() {
        val g = game
        if (g == null || g.status != SudokuStatus.PLAYING) {
            stopClock()
            render()
            return
        }
        val next = if (holdActive()) {
            val el = currentElapsed()
            stopClock()
            SudokuGameEngine.pause(g, el)
        } else {
            SudokuGameEngine.resume(g).also { if (clockJob == null) startClock(it.elapsedMs) }
        }
        game = next
        _timeText.value = SudokuEntryUiMapper.formatTime(next.elapsedMs)
        render()
    }

    private fun startClock(base: Long) {
        clockJob?.cancel()
        clockBase = base
        clockOrigin = time.monotonicMillis()
        clockJob = viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                val g = game ?: continue
                val next = SudokuGameEngine.tick(g, clockBase + (time.monotonicMillis() - clockOrigin))
                if (next !== g) {
                    game = next
                    _timeText.value = SudokuEntryUiMapper.formatTime(next.elapsedMs)
                }
            }
        }
    }

    private fun stopClock() {
        clockJob?.cancel()
        clockJob = null
    }

    private fun currentElapsed(): Long {
        val g = game ?: return 0L
        return if (clockJob != null) maxOf(g.elapsedMs, clockBase + (time.monotonicMillis() - clockOrigin)) else g.elapsedMs
    }

    // ------------------------------------------------------------------ dialogs

    fun onRestartClick() {
        val g = game ?: return
        if (g.status != SudokuStatus.PLAYING || dialog != SudokuDialogUi.None) return
        setDialog(SudokuDialogUi.ConfirmRestart)
    }

    fun onHelpClick() {
        val g = game ?: return
        if (g.status != SudokuStatus.PLAYING || dialog != SudokuDialogUi.None) return
        setDialog(SudokuDialogUi.Help)
    }

    /** The route opens / closes the "out of hints" dialog (it knows whether rewarded ads are allowed). */
    fun onHintDialog(show: Boolean) {
        if (show) {
            if (dialog == SudokuDialogUi.None) setDialog(SudokuDialogUi.HintNeeded)
        } else if (dialog == SudokuDialogUi.HintNeeded) {
            setDialog(SudokuDialogUi.None)
        }
    }

    fun onDismissDialog() {
        if (dialog.pausesClock) setDialog(SudokuDialogUi.None)
    }

    private fun setDialog(next: SudokuDialogUi) {
        dialog = next
        applyHolds()
    }

    /** Confirmed Restart, or game-over Retry: same puzzle, fresh board, clock from zero. */
    fun onRestartConfirmed() = restart()

    fun onRetry() = restart()

    private fun restart() {
        val g = game ?: return
        if (dialog != SudokuDialogUi.None && dialog !is SudokuDialogUi.GameOver && !dialog.pausesClock) return
        stopClock()
        saveJob?.cancel()
        val fresh = SudokuGameEngine.restart(g)
        completed = false
        userPaused = false
        dialog = SudokuDialogUi.None
        game = fresh
        _timeText.value = SudokuEntryUiMapper.formatTime(0L)
        viewModelScope.launch { persist() }
        applyHolds()
    }

    // ------------------------------------------------------------------ finish

    private fun finish(state: SudokuGameState) {
        if (completed) return
        completed = true
        saveJob?.cancel()
        viewModelScope.launch {
            val result = try {
                ioLock.withLock { completeSudoku(state) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                SudokuCompletion(won = state.status == SudokuStatus.SOLVED)
            }
            dialog = if (state.status == SudokuStatus.SOLVED) {
                SudokuDialogUi.Won(SudokuGameUiMapper.win(state, result))
            } else {
                SudokuDialogUi.GameOver(state.mistakes)
            }
            render()
        }
    }

    /** Win dialog "Next puzzle": interstitial if allowed (fail open), then a fresh puzzle of the same tier. */
    fun onWinNext() = afterWin {
        game = null
        dialog = SudokuDialogUi.None
        render()
        loadGame()
    }

    /** Win dialog "Back": interstitial if allowed (fail open), then back to the entry. */
    fun onWinDone() = afterWin { exit(SudokuExitReason.NONE) }

    private fun afterWin(next: suspend () -> Unit) {
        if (winBusy) return
        winBusy = true
        viewModelScope.launch {
            try {
                try {
                    showInterstitial()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // fail open
                }
                next()
            } finally {
                winBusy = false
            }
        }
    }

    // ------------------------------------------------------------------ leave

    /** System / top-bar back. Closes a dialog first; otherwise pauses, saves, leaves. */
    fun onBack() {
        if (dialog.pausesClock) {
            setDialog(SudokuDialogUi.None)
            return
        }
        leave()
    }

    /** Game-over "Quit". */
    fun onQuit() = leave()

    private fun leave() {
        if (leaving) return
        leaving = true
        applyHolds()
        viewModelScope.launch {
            persist()
            exit(SudokuExitReason.NONE)
        }
    }

    private var exited = false

    private fun exit(reason: SudokuExitReason) {
        if (exited) return
        exited = true
        eventChannel.trySend(SudokuGameEvent.Exit(reason))
    }

    // ------------------------------------------------------------------ save + render

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persist()
        }
    }

    private fun flushSave() {
        saveJob?.cancel()
        viewModelScope.launch { persist() }
    }

    /** Saves the LATEST state (re-read under the lock). Skips finished games and anything after [completed]. */
    private suspend fun persist() {
        try {
            withContext(NonCancellable) {
                ioLock.withLock {
                    val g = game ?: return@withLock
                    if (completed || g.status != SudokuStatus.PLAYING) return@withLock
                    saveGame(g)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // best effort: the next change saves again
        }
    }

    private fun render() {
        val g = game
        _state.value = if (g == null) {
            SudokuGameUiState(loading = true, tier = tier ?: SudokuTier.EASY, hintCharges = hintCharges)
        } else {
            SudokuGameUiMapper.map(g, hintCharges, userPaused, dialog)
        }
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val SAVE_DEBOUNCE_MS = 1_000L
    }
}
