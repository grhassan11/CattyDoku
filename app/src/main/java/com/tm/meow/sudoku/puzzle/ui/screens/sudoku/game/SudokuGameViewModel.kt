package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsEvent
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.AttemptMode
import com.tm.meow.sudoku.puzzle.domain.analytics.AttemptStats
import com.tm.meow.sudoku.puzzle.domain.analytics.AttemptTracker
import com.tm.meow.sudoku.puzzle.domain.analytics.LevelContext
import com.tm.meow.sudoku.puzzle.domain.analytics.QuitReason
import com.tm.meow.sudoku.puzzle.domain.analytics.NoopAnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.crash.CrashReporter
import com.tm.meow.sudoku.puzzle.domain.crash.NoopCrashReporter
import com.tm.meow.sudoku.puzzle.domain.model.Booster
import com.tm.meow.sudoku.puzzle.domain.model.Settings
import com.tm.meow.sudoku.puzzle.domain.sudoku.engine.SudokuGameEngine
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuGameState
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuStatus
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.CompleteSudoku
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.SaveOrDiscardSudokuGame
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.SaveSudokuGame
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.ShowSudokuInterstitialIfAllowed
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.StartOrResumeSudoku
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.SudokuCompletion
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.SudokuHintResult
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.SudokuStartResult
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.UseSudokuHint
import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveBoosterBalances
import com.tm.meow.sudoku.puzzle.domain.usecase.NoopUserPropsSync
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveSettings
import com.tm.meow.sudoku.puzzle.domain.usecase.UserPropsSync
import com.tm.meow.sudoku.puzzle.ui.navigation.Routes
import com.tm.meow.sudoku.puzzle.ui.screens.game.HapticKind
import com.tm.meow.sudoku.puzzle.ui.screens.game.SfxKind
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.entry.SudokuEntryUiMapper
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
 * app stopped (ON_PAUSE), an open Restart / hint dialog, the tutorial (route calls onAppStopped), leaving. Every hold pauses the engine state; the pause
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
    private val saveOrDiscard: SaveOrDiscardSudokuGame,
    private val useHint: UseSudokuHint,
    private val completeSudoku: CompleteSudoku,
    private val showInterstitial: ShowSudokuInterstitialIfAllowed,
    observeBoosters: ObserveBoosterBalances,
    observeSettings: ObserveSettings,
    private val time: TimeProvider,
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
    private val userProps: UserPropsSync = NoopUserPropsSync,
    private val crash: CrashReporter = NoopCrashReporter,
) : ViewModel() {

    private val tier: SudokuTier? = savedState.get<String>(Routes.ARG_SUDOKU_TIER)
        ?.let { id -> SudokuTier.entries.firstOrNull { it.name == id } }

    private var game: SudokuGameState? = null
    private val attempt = AttemptTracker()
    private var hintCharges = 0
    private var settings = Settings()
    private var dialog: SudokuDialogUi = SudokuDialogUi.None
    private var userPaused = false
    private var appStopped = false
    private var leaving = false
    private var completed = false
    private var hinting = false
    private var winBusy = false
    private var restartBusy = false

    /** Lost state whose `level_end` (lose) is held back: the rewarded continue can still revive it. Logged when the loss truly ends. */
    private var pendingLoss: SudokuGameState? = null

    /** Lifetime wins in this tier INCLUDING the win now on screen (from [CompleteSudoku]); read by the win ad rule. */
    private var winCount: Int? = null

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
            try {
                observeSettings().collect { settings = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // keep defaults (sound + haptics on)
            }
        }
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
            crash.recordNonFatal(IllegalStateException("sudoku_load_failed: missing tier"))
            exit(SudokuExitReason.FAILED)
            return
        }
        var recorded = false
        val result = try {
            startOrResume(t)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            crash.recordNonFatal(e, "sudoku_load_failed")
            recorded = true
            SudokuStartResult.Failed(e.message.orEmpty())
        }
        when (result) {
            is SudokuStartResult.Locked -> exit(SudokuExitReason.LOCKED)
            is SudokuStartResult.Failed -> {
                if (!recorded) {
                    crash.recordNonFatal(IllegalStateException("sudoku_generate_failed: ${result.reason}"), "sudoku_generate_failed")
                }
                exit(SudokuExitReason.FAILED)
            }
            is SudokuStartResult.Resumed -> {
                attempt.begin(attemptKey(result.state), resumed = true)
                if (result.state.status == SudokuStatus.PLAYING) {
                    analytics.log(AnalyticsEvent.LevelResume(levelContext(result.state), attemptStats(result.state, result.state.elapsedMs)))
                }
                begin(result.state)
            }
            is SudokuStartResult.Started -> {
                begin(result.state)
                logStart(result.state)
            }
        }
    }

    private fun begin(state: SudokuGameState) {
        crash.setCustomKey("mode", "sudoku")
        crash.setCustomKey("tier", state.tier.analyticsId())
        crash.setCustomKey("board_size", state.size.n)
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

    fun onCellTap(cell: Int) = act(save = false, cue = Cue.SELECT) { g, _ -> SudokuGameEngine.select(g, cell) }

    fun onDigit(digit: Int) = act(cue = Cue.DIGIT) { g, el -> SudokuGameEngine.enterDigit(g, digit, el) }

    fun onUndo() = act(cue = Cue.REMOVE) { g, _ ->
        SudokuGameEngine.undo(g).also { if (it !== g) analytics.log(AnalyticsEvent.UndoUsed(levelContext(g))) }
    }

    fun onErase() = act(cue = Cue.REMOVE) { g, _ -> SudokuGameEngine.erase(g) }

    fun onToggleNotes() = act(save = false) { g, _ -> SudokuGameEngine.toggleNotesMode(g) }

    fun onHint() {
        val g = game ?: return
        if (!g.isActive || hinting) return
        hinting = true
        viewModelScope.launch {
            try {
                when (val r = useHint(g, currentElapsed())) {
                    // Charge is spent: apply to the LATEST state (the player may have moved while it was spent).
                    is SudokuHintResult.Granted -> {
                        analytics.log(AnalyticsEvent.HintUsed(levelContext(g)))
                        act(cue = Cue.HINT) { latest, el -> SudokuGameEngine.applyHint(latest, r.hint, el) }
                    }
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

    private fun act(save: Boolean = true, cue: Cue? = null, transform: (SudokuGameState, Long) -> SudokuGameState) {
        val g = game ?: return
        val next = transform(g, currentElapsed())
        if (next === g) return
        if (cue != null) feedback(g, next, cue)
        commit(next, save)
    }

    /** What the player did; picks the sound + haptic. Outcomes (win / mistake / game over) override the action cue. */
    private enum class Cue { SELECT, DIGIT, REMOVE, HINT }

    private fun feedback(before: SudokuGameState, after: SudokuGameState, cue: Cue) {
        when {
            after.status == SudokuStatus.SOLVED -> { sfx(SfxKind.WIN); haptic(HapticKind.WIN) }
            after.status == SudokuStatus.GAME_OVER -> { sfx(SfxKind.WRONG); haptic(HapticKind.LOSE) }
            after.mistakes > before.mistakes -> { sfx(SfxKind.WRONG); haptic(HapticKind.WRONG) }
            cue == Cue.HINT -> { sfx(SfxKind.HINT); haptic(HapticKind.PLACE) }
            cue == Cue.DIGIT && before.notesMode -> { sfx(SfxKind.UI_TAP); haptic(HapticKind.TICK) }
            cue == Cue.DIGIT -> { sfx(SfxKind.CHIME_SOLO); haptic(HapticKind.CORRECT) }
            cue == Cue.REMOVE -> { sfx(SfxKind.CROSS); haptic(HapticKind.TICK) }
            else -> haptic(HapticKind.TICK) // SELECT: haptic only (Queens parity)
        }
    }

    private fun sfx(kind: SfxKind) {
        if (settings.sfx) eventChannel.trySend(SudokuGameEvent.Sfx(kind))
    }

    private fun haptic(kind: HapticKind) {
        if (settings.haptics) eventChannel.trySend(SudokuGameEvent.Haptic(kind))
    }

    private fun SudokuTier.analyticsId(): String = name.lowercase()

    /** Fresh attempt (new puzzle or restart): opens a new attempt and logs `level_start`. */
    private fun logStart(state: SudokuGameState) {
        attempt.begin(attemptKey(state))
        analytics.log(AnalyticsEvent.LevelStart(levelContext(state)))
    }

    // ---- analytics helpers ----

    private fun attemptKey(state: SudokuGameState): String = state.tier.analyticsId()

    private fun levelContext(state: SudokuGameState) = LevelContext(
        mode = AttemptMode.SUDOKU,
        levelNumber = null,
        world = null,
        boardSize = state.size.n,
        difficulty = state.tier.analyticsId(),
        attemptNo = attempt.attemptNo,
    )

    /** [durationSec] excludes pauses (game clock). Sudoku lives = mistakes. */
    private fun attemptStats(state: SudokuGameState, elapsedMs: Long = currentElapsed(), score: Int? = null) = AttemptStats(
        durationSec = (elapsedMs / 1000L).toInt(),
        livesLost = state.mistakes,
        hintsUsed = state.hintsUsed,
        boostersUsed = 0,
        score = score,
        resumed = attempt.resumed,
    )

    /** Logs `level_quit` once per open attempt. Never on app background (ads / shop / dialogs also hold the clock). */
    private fun maybeLogQuit(reason: String) {
        val g = game ?: return
        if (!attempt.consumeQuit()) return
        analytics.log(AnalyticsEvent.LevelQuit(levelContext(g), attemptStats(g), reason))
    }

    /** The game-over truly ended (restart / leave / screen cleared): log the held `level_end` (lose) once. */
    private fun logPendingLoss() {
        val s = pendingLoss ?: return
        pendingLoss = null
        analytics.log(
            AnalyticsEvent.LevelEnd(ctx = levelContext(s), stats = attemptStats(s, s.elapsedMs, s.score), won = false),
        )
    }

    override fun onCleared() {
        logPendingLoss()
        maybeLogQuit(QuitReason.CLEARED)
        super.onCleared()
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
        if (userPaused) analytics.log(AnalyticsEvent.GamePause(levelContext(g), attemptStats(g)))
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

    /** Confirmed Restart (dialog) or game-over Retry: [restartWithAd] (ad first for non-Easy tiers), then same puzzle, fresh board, clock from zero. */
    fun onRestart() = restartWithAd()

    /**
     * Interstitial FIRST (non-Easy tiers; fail open), only then the fresh board and the clock. The Restart dialog (clock hold)
     * stays up while the ad loads / shows, so the clock cannot run before the ad is dismissed. If the player closed the
     * dialog during the short load wait, the restart is dropped (no surprise reset).
     */
    private fun restartWithAd() {
        val g = game ?: return
        if (dialog != SudokuDialogUi.None && dialog !is SudokuDialogUi.GameOver && !dialog.pausesClock) return
        if (restartBusy) return
        restartBusy = true
        val before = dialog
        val t = g.tier
        viewModelScope.launch {
            try {
                try {
                    showInterstitial.onRestart(t)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // fail open
                }
                if (dialog == before) restart()
            } finally {
                restartBusy = false
            }
        }
    }

    private fun restart() {
        val g = game ?: return
        stopClock()
        saveJob?.cancel()
        logPendingLoss() // restart after a game over ends that attempt (before the new one starts)
        val fresh = SudokuGameEngine.restart(g)
        analytics.log(AnalyticsEvent.LevelRestart(levelContext(g), attemptStats(g))) // restart is NOT a quit
        logStart(fresh)
        completed = false
        winCount = null
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
        attempt.end() // a finished attempt can never be logged as a quit
        saveJob?.cancel()
        val ctx = levelContext(state)
        viewModelScope.launch {
            val result = try {
                ioLock.withLock { completeSudoku(state) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                SudokuCompletion(won = state.status == SudokuStatus.SOLVED)
            }
            val won = state.status == SudokuStatus.SOLVED
            if (won) {
                analytics.log(
                    AnalyticsEvent.LevelEnd(
                        ctx = ctx,
                        stats = attemptStats(state, state.elapsedMs, state.score),
                        won = true,
                        newBestTime = result.isNewBestTime,
                        newBestScore = result.isNewBestScore,
                    ),
                )
            } else {
                pendingLoss = state // `level_end` (lose) waits: a rewarded continue may still revive this game
            }
            if (won) for (t in result.justUnlocked) analytics.log(AnalyticsEvent.SudokuTierUnlock(t.analyticsId()))
            try {
                userProps.sync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // best effort
            }
            winCount = if (state.status == SudokuStatus.SOLVED) result.progress?.wins else null
            dialog = if (state.status == SudokuStatus.SOLVED) {
                SudokuDialogUi.Won(SudokuGameUiMapper.win(state, result))
            } else {
                SudokuDialogUi.GameOver(state.mistakes)
            }
            render()
        }
    }

    /**
     * Game-over rewarded ad earned: continue the SAME game with one heart (Levels revive parity, no cap). Only valid while
     * the game-over dialog shows. Called ONLY after the earned-reward result, never on dismiss. Nothing is recorded as a
     * loss: the held `level_end` is dropped, the attempt reopens, the autosave that the game-over cleared is written back
     * and the clock resumes.
     */
    fun onRevive() {
        val g = game ?: return
        if (g.status != SudokuStatus.GAME_OVER || dialog !is SudokuDialogUi.GameOver) return
        val revived = SudokuGameEngine.revive(g)
        if (revived === g) return
        analytics.log(AnalyticsEvent.LevelRevive(levelContext(g), attemptStats(g, g.elapsedMs, g.score)))
        pendingLoss = null
        completed = false
        attempt.revive()
        userPaused = false
        dialog = SudokuDialogUi.None
        game = revived
        _timeText.value = SudokuEntryUiMapper.formatTime(revived.elapsedMs)
        viewModelScope.launch { persist() } // finish() cleared the save on the game over
        applyHolds() // PLAYING again: clock restarts unless something else holds it
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
                    game?.let { showInterstitial.onWin(it.tier, winCount) }
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

    /** Game-over "Quit" (the attempt is already over, so this never logs a quit). */
    fun onQuit() = leave()

    private fun leave() {
        if (leaving) return
        leaving = true
        logPendingLoss()
        maybeLogQuit(QuitReason.SYSTEM_BACK)
        applyHolds()
        viewModelScope.launch {
            persist(idleExit = true) // idle (no moves, < 5 s): clears the save, so no CONTINUE
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
        viewModelScope.launch { persist(idleExit = true) }
    }

    /** Saves the LATEST state (re-read under the lock). Skips finished games and anything after [completed]. */
    private suspend fun persist(idleExit: Boolean = false) {
        try {
            withContext(NonCancellable) {
                ioLock.withLock {
                    val g = game ?: return@withLock
                    if (completed || g.status != SudokuStatus.PLAYING) return@withLock
                    if (idleExit) saveOrDiscard(g) else saveGame(g)
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
