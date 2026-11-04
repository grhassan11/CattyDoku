package com.tm.meow.sudoku.puzzle.ui.screens.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tm.meow.sudoku.puzzle.audio.AudioPolicy
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
import com.tm.meow.sudoku.puzzle.domain.engine.DifficultyCurve
import com.tm.meow.sudoku.puzzle.domain.engine.MouseBoosterPolicy
import com.tm.meow.sudoku.puzzle.domain.model.Booster
import com.tm.meow.sudoku.puzzle.domain.model.CellMark
import com.tm.meow.sudoku.puzzle.domain.model.GameEvent
import com.tm.meow.sudoku.puzzle.domain.model.GameState
import com.tm.meow.sudoku.puzzle.domain.model.GameStatus
import com.tm.meow.sudoku.puzzle.domain.model.Hint
import com.tm.meow.sudoku.puzzle.domain.model.PieceSkin
import com.tm.meow.sudoku.puzzle.domain.model.Pos
import com.tm.meow.sudoku.puzzle.domain.model.Puzzle
import com.tm.meow.sudoku.puzzle.domain.model.Settings
import com.tm.meow.sudoku.puzzle.domain.model.StepResult
import com.tm.meow.sudoku.puzzle.domain.model.WorldRules
import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.AnalyseBoard
import com.tm.meow.sudoku.puzzle.domain.usecase.ApplyDragX
import com.tm.meow.sudoku.puzzle.domain.usecase.ApplyStarterCat
import com.tm.meow.sudoku.puzzle.domain.usecase.CatBoosterResult
import com.tm.meow.sudoku.puzzle.domain.usecase.CompleteLevel
import com.tm.meow.sudoku.puzzle.domain.usecase.DailyLoadResult
import com.tm.meow.sudoku.puzzle.domain.usecase.ComputeScore
import com.tm.meow.sudoku.puzzle.domain.usecase.ConsumeBooster
import com.tm.meow.sudoku.puzzle.domain.usecase.GetBoosterCharges
import com.tm.meow.sudoku.puzzle.domain.usecase.GetOrCreatePuzzle
import com.tm.meow.sudoku.puzzle.domain.usecase.HintResult
import com.tm.meow.sudoku.puzzle.domain.usecase.MarkLevelPlayed
import com.tm.meow.sudoku.puzzle.domain.usecase.MouseBoosterResult
import com.tm.meow.sudoku.puzzle.domain.usecase.NoopUserPropsSync
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveSettings
import com.tm.meow.sudoku.puzzle.domain.usecase.PlaceCat
import com.tm.meow.sudoku.puzzle.domain.usecase.PrefetchLevel
import com.tm.meow.sudoku.puzzle.domain.usecase.PuzzleLoadResult
import com.tm.meow.sudoku.puzzle.domain.usecase.RecordPlayedToday
import com.tm.meow.sudoku.puzzle.domain.usecase.ResolveDaily
import com.tm.meow.sudoku.puzzle.domain.usecase.ResolveStartLevel
import com.tm.meow.sudoku.puzzle.domain.usecase.RestoreGame
import com.tm.meow.sudoku.puzzle.domain.usecase.ReviveGame
import com.tm.meow.sudoku.puzzle.domain.usecase.SaveGame
import com.tm.meow.sudoku.puzzle.domain.usecase.SeedStarterBoosters
import com.tm.meow.sudoku.puzzle.domain.usecase.ToggleX
import com.tm.meow.sudoku.puzzle.domain.usecase.Undo
import com.tm.meow.sudoku.puzzle.domain.usecase.UpdateSettings
import com.tm.meow.sudoku.puzzle.domain.usecase.UserPropsSync
import com.tm.meow.sudoku.puzzle.domain.usecase.UseCatBooster
import com.tm.meow.sudoku.puzzle.domain.usecase.UseHint
import com.tm.meow.sudoku.puzzle.domain.usecase.UseMouseBooster
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Game screen state holder. Injects use cases only (plus the domain [TimeProvider] clock seam).
 * All mutation happens on the main dispatcher; repository calls are main-safe suspend functions and
 * puzzle generation hops to Dispatchers.Default inside [GetOrCreatePuzzle].
 *
 * Timer: nothing is displayed, so no ticker. Elapsed = persisted base + running monotonic delta,
 * paused by [onAppStopped], resumed by [onAppStarted], frozen on win / loss.
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val resolveStartLevel: ResolveStartLevel,
    private val getOrCreatePuzzle: GetOrCreatePuzzle,
    private val restoreGame: RestoreGame,
    private val saveGame: SaveGame,
    private val completeLevel: CompleteLevel,
    private val computeScore: ComputeScore,
    private val analyseBoard: AnalyseBoard,
    private val toggleX: ToggleX,
    private val placeCat: PlaceCat,
    private val applyDragX: ApplyDragX,
    private val undo: Undo,
    private val useHint: UseHint,
    private val useCatBooster: UseCatBooster,
    private val useMouseBooster: UseMouseBooster,
    private val applyStarterCat: ApplyStarterCat,
    private val recordPlayedToday: RecordPlayedToday,
    private val markLevelPlayed: MarkLevelPlayed,
    private val observeSettings: ObserveSettings,
    private val updateSettings: UpdateSettings,
    private val getBoosterCharges: GetBoosterCharges,
    private val consumeBooster: ConsumeBooster,
    private val seedStarterBoosters: SeedStarterBoosters,
    private val prefetchLevel: PrefetchLevel,
    private val time: TimeProvider,
    private val resolveDaily: ResolveDaily,
    private val reviveGame: ReviveGame,
    savedStateHandle: SavedStateHandle,
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
    private val crash: CrashReporter = NoopCrashReporter,
    private val userProps: UserPropsSync = NoopUserPropsSync,
) : ViewModel() {

    /**
     * Daily Challenge mode (nav arg [ARG_DAILY]). Same screen and engine as a level, but: the puzzle comes
     * from [ResolveDaily] (negative storage key), lastPlayedLevel / next-level prefetch are skipped, and
     * the win dialog says "Daily complete!". Completion is recorded by [CompleteLevel] under that key.
     */
    private val isDaily: Boolean = savedStateHandle.get<Boolean>(ARG_DAILY) ?: false

    /**
     * Level the Home map asked for (nav arg [ARG_LEVEL], > 0): the current node plays it, a finished node replays it.
     * Null = resolve with [ResolveStartLevel]. Ignored for the daily.
     */
    private val requestedLevel: Int? = if (isDaily) null else savedStateHandle.get<Int>(ARG_LEVEL)?.takeIf { it > 0 }

    private data class Session(
        val puzzle: Puzzle,
        val state: GameState,
        val hint: Hint? = null,
        /** Hint dim + rule card is showing. UI-only: dismissing never touches charges or score. */
        val hintOverlay: Boolean = false,
        val outcome: OutcomeUi? = null,
        /** Mouse flight playing (UI-only animation; the X marks are already in [state]). Blocks input until cleared. */
        val mouseRun: MouseRunUi? = null,
        /** Latest correct placement; set only by [applyStep], never by load / restore / undo. */
        val correctPlacement: CorrectPlacementUi? = null,
    )

    private var mouseRunSeq = 0
    private var placementSeq = 0

    private class SaveRequest(val level: Int, val state: GameState, val elapsedMs: Long)

    private val _state = MutableStateFlow<GameUiState>(GameUiState.Loading(null))
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _events = Channel<GameUiEvent>(Channel.BUFFERED)
    val events: Flow<GameUiEvent> = _events.receiveAsFlow()

    private val attempt = AttemptTracker()

    /** The level being played is a replay of an already-reached level (approximation: requested < furthest level). */
    private var isReplay = false

    private var settings = Settings()
    private var session: Session? = null
    private var loadJob: Job? = null

    // timer
    private var elapsedBaseMs = 0L
    private var runningSinceMs: Long? = null
    private var appActive = true

    /** Latest-wins autosave queue: one writer, intermediate states may be dropped. */
    private val saveQueue = Channel<SaveRequest>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            observeSettings().collect {
                settings = it
                publish()
            }
        }
        viewModelScope.launch {
            for (req in saveQueue) bestEffort { saveGame(req.level, req.state, req.elapsedMs) }
        }
        loadLevel(requestedLevel)
    }

    // ---------------------------------------------------------------- loading

    /** Retry after a [GameUiState.Failed]. */
    fun onRetryLoad() {
        val failed = _state.value as? GameUiState.Failed ?: return
        loadLevel(if (isDaily) null else failed.levelNumber)
    }

    /** Win dialog "Level N+1": open the next level. */
    fun onNextLevel() {
        val s = session ?: return
        if (isDaily || s.outcome !is OutcomeUi.Won) return
        loadLevel(s.puzzle.levelIndex + 1)
    }

    /** Restart the current level from scratch (lose-dialog "Retry" only). Fresh autosave replaces the old one. */
    fun onRestart() {
        val s = session ?: return
        viewModelScope.launch {
            val charges = bestEffortResult { getBoosterCharges() } ?: s.state.charges
            pauseTimer()
            analytics.log(AnalyticsEvent.LevelRestart(levelContext(s.puzzle), attemptStats(s))) // restart is NOT a quit
            elapsedBaseMs = 0L
            val fresh = applyStarterCat(s.puzzle, GameState.initial(s.puzzle.size, charges))
            session = Session(s.puzzle, fresh)
            logStart(s.puzzle)
            resumeTimer()
            enqueueSave()
            publish()
        }
    }

    /** Settings dialog toggle. Persisted; the settings flow collected in init republishes the UI state. */
    fun onSettingChanged(toggle: SettingToggle, enabled: Boolean) {
        analytics.log(AnalyticsEvent.SettingChange(toggle.name.lowercase(), enabled))
        viewModelScope.launch {
            bestEffort {
                updateSettings {
                    when (toggle) {
                        SettingToggle.MUSIC -> it.copy(music = enabled)
                        SettingToggle.SFX -> it.copy(sfx = enabled)
                        SettingToggle.VOICE -> it.copy(voice = enabled)
                        SettingToggle.HAPTICS -> it.copy(haptics = enabled)
                        SettingToggle.PATTERN_MODE -> it.copy(patternMode = enabled)
                        SettingToggle.STREAK_REMINDERS -> it.copy(streakReminders = enabled)
                        SettingToggle.DAILY_REMINDERS -> it.copy(dailyReminders = enabled)
                    }
                }
            }
        }
    }

    private fun loadLevel(requested: Int?) {
        loadJob?.cancel()
        maybeLogQuit(QuitReason.SWITCH_LEVEL)
        session = null
        pauseTimer()
        elapsedBaseMs = 0L
        _state.value = GameUiState.Loading(requested)
        loadJob = viewModelScope.launch {
            var level = requested ?: 0
            try {
                bestEffort { seedStarterBoosters() }
                val puzzle: Puzzle = if (isDaily) {
                    when (val d = resolveDaily()) {
                        is DailyLoadResult.Ready -> d.puzzle.also { level = it.levelIndex } // negative daily key
                        is DailyLoadResult.Failed -> {
                            _state.value = GameUiState.Failed(0, d.reason)
                            return@launch
                        }
                    }
                } else {
                    val frontier = resolveStartLevel()
                    level = requested ?: frontier
                    isReplay = requested != null && requested < frontier
                    _state.value = GameUiState.Loading(level)
                    when (val r = getOrCreatePuzzle(level)) {
                        is PuzzleLoadResult.Cached -> r.puzzle
                        is PuzzleLoadResult.Created -> r.puzzle
                        is PuzzleLoadResult.Failed -> {
                            crash.recordNonFatal(IllegalStateException("puzzle_load_failed: ${r.reason}"))
                            _state.value = GameUiState.Failed(level, r.reason)
                            return@launch
                        }
                    }
                }
                val charges = getBoosterCharges()
                val restored = restoreGame(level)

                val usedPuzzle: Puzzle
                val gameState: GameState
                var needsSave = false
                if (restored != null) {
                    usedPuzzle = restored.puzzle
                    // Inventory is the source of truth for charges, the save only carries a mirror.
                    val base = restored.saved.state.copy(charges = charges)
                    // Untouched save (pre-feature autosave, or saved before first move): no cat, no
                    // locked cell, no undo history -> still owes the starter cat. Real progress stays as is.
                    val untouched = base.isPlaying && base.undoStack.isEmpty() && base.lockedCells.isEmpty() &&
                        base.board.marks.none { it == CellMark.CAT }
                    gameState = if (untouched) applyStarterCat(usedPuzzle, base) else base
                    needsSave = gameState !== base
                    elapsedBaseMs = restored.saved.elapsedMs
                } else {
                    usedPuzzle = puzzle
                    // Fresh start: no save at all.
                    gameState = applyStarterCat(puzzle, GameState.initial(puzzle.size, charges))
                    elapsedBaseMs = 0L
                    logStart(puzzle)
                }
                val resumedSave = restored != null
                crash.setCustomKey("level", usedPuzzle.levelIndex)
                crash.setCustomKey("board_size", usedPuzzle.size)
                crash.setCustomKey("daily", isDaily)
                // Self-heal a stuck save: PLAYING but board already solved -> record the win once.
                val stuckSolved = gameState.isPlaying && analyseBoard(usedPuzzle, gameState.board).solved
                session = Session(
                    usedPuzzle,
                    if (stuckSolved) gameState.copy(status = GameStatus.SOLVED) else gameState,
                )
                if (resumedSave) {
                    attempt.begin(attemptKey(usedPuzzle), resumed = true)
                    if (!stuckSolved) {
                        analytics.log(AnalyticsEvent.LevelResume(levelContext(usedPuzzle), attemptStats(checkNotNull(session))))
                    }
                }
                if (stuckSolved) {
                    finish(won = true) // pauses timer, sets outcome, completeLevel (clears autosave), emits LevelWon
                } else {
                    resumeTimer()
                    if (needsSave) enqueueSave()
                }

                if (!isDaily) bestEffort { markLevelPlayed(level) }
                if (RECORD_STREAK_ON_LEVEL_START) bestEffort { recordPlayedToday() }
                publish()

                // Next-level prefetch: cache-first, generation on Dispatchers.Default inside the use case.
                if (!isDaily) viewModelScope.launch { prefetchLevel(level + 1) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                crash.recordNonFatal(t, "level_load_failed")
                _state.value = GameUiState.Failed(level.coerceAtLeast(1), t.message ?: t.javaClass.simpleName)
            }
        }
    }

    // ---------------------------------------------------------------- board input

    fun onCellTap(pos: Pos) {
        dragCrossFeedback = 0
        val s = idle() ?: return
        applyStep(s, toggleX(s.puzzle, s.state, pos), HapticKind.TICK, pos)
    }

    /** Place a cat (replaces X) or remove the cat that is there. */
    fun onCellDoubleTap(pos: Pos) {
        val s = idle() ?: return
        if (pos.index(s.puzzle.size) in s.state.lockedCells) {
            sendSfx(SfxKind.BLOCKED) // double-tap on a starter / booster cat: refused
            return
        }
        applyStep(s, placeCat(s.puzzle, s.state, pos), HapticKind.TICK, pos)
    }

    /** Live drag feedback: one cross sfx + light tick per cell newly marked X as the finger enters it. */
    fun onDragCross() {
        if (idle() == null) return
        dragCrossFeedback++
        sendHaptic(HapticKind.TICK)
        sendSfx(SfxKind.CROSS)
    }

    private var dragCrossFeedback = 0

    fun onDrag(path: List<Pos>) {
        val fired = dragCrossFeedback
        dragCrossFeedback = 0
        val s = idle() ?: return
        // Per-cell feedback already fired live; skip the end-of-gesture duplicate.
        applyStep(
            s, applyDragX(s.puzzle, s.state, path), HapticKind.TICK, null,
            crossSfx = fired == 0, haptic = fired == 0,
        )
    }

    fun onUndo() {
        val s = idle() ?: return
        val step = undo(s.state)
        if (step.state !== s.state) analytics.log(AnalyticsEvent.UndoUsed(levelContext(s.puzzle)))
        applyStep(s, step, HapticKind.TICK, null, crossSfx = false)
    }

    // ---------------------------------------------------------------- boosters

    fun onHint() {
        val s = idle() ?: return
        when (val r = useHint(s.puzzle, s.state)) {
            HintResult.NoCharge -> {
                _events.trySend(GameUiEvent.NoCharges(Booster.HINT))
                sendSfx(SfxKind.BLOCKED)
            }
            HintResult.Unavailable -> Unit
            is HintResult.Granted -> {
                val spent = r.state.chargesOf(Booster.HINT) < s.state.chargesOf(Booster.HINT)
                val shown = r.hint.takeUnless { it is Hint.None }
                session = s.copy(state = r.state, hint = shown, hintOverlay = shown != null)
                if (spent) {
                    analytics.log(AnalyticsEvent.HintUsed(levelContext(s.puzzle)))
                    viewModelScope.launch { bestEffort { consumeBooster(Booster.HINT) } }
                }
                if (session?.hint != null) sendSfx(SfxKind.HINT)
                enqueueSave()
                publish()
            }
        }
    }

    /** Hint dim overlay closed (tap anywhere / Got it / back). Pure UI: no charge, no score, hint highlight stays until the next move. */
    fun onHintOverlayDismiss() {
        val s = session ?: return
        if (!s.hintOverlay) return
        session = s.copy(hintOverlay = false)
        publish()
    }

    fun onCatBooster() {
        val s = idle() ?: return
        when (val r = useCatBooster(s.puzzle, s.state)) {
            CatBoosterResult.NoCharge -> {
                _events.trySend(GameUiEvent.NoCharges(Booster.CAT))
                sendSfx(SfxKind.BLOCKED)
            }
            CatBoosterResult.Unavailable -> Unit
            is CatBoosterResult.Placed -> {
                attempt.boosterUsed()
                analytics.log(AnalyticsEvent.BoosterUsed("cat", levelContext(s.puzzle)))
                viewModelScope.launch { bestEffort { consumeBooster(Booster.CAT) } }
                applyStep(s, r.step, HapticKind.PLACE, r.cell)
            }
        }
    }

    /** Mouse booster: 3 random safe X marks. Locked in the daily and below the unlock level (button is inert there). */
    fun onMouseBooster() {
        val s = idle() ?: return
        if (isDaily || !MouseBoosterPolicy.isUnlocked(s.puzzle.levelIndex)) return
        when (val r = useMouseBooster(s.puzzle, s.state)) {
            MouseBoosterResult.NoCharge -> {
                _events.trySend(GameUiEvent.NoCharges(Booster.MOUSE))
                sendSfx(SfxKind.BLOCKED)
            }
            MouseBoosterResult.Unavailable -> Unit // nothing eligible: no charge spent
            is MouseBoosterResult.Marked -> {
                attempt.boosterUsed()
                analytics.log(AnalyticsEvent.BoosterUsed("mouse", levelContext(s.puzzle)))
                viewModelScope.launch { bestEffort { consumeBooster(Booster.MOUSE) } }
                // Only X marks were added: cannot solve, lose or conflict, so no outcome check is needed.
                // State (X marks, charge, save) is final right now; the UI only animates the reveal and then calls
                // onMouseAnimDone. Input is blocked meanwhile via idle(). onAppStopped also releases it.
                session = s.copy(
                    state = r.state, hint = null, hintOverlay = false,
                    mouseRun = MouseRunUi(++mouseRunSeq, r.cells),
                )
                sendHaptic(HapticKind.TICK)
                sendSfx(SfxKind.MOUSE)
                enqueueSave()
                publish()
            }
        }
    }

    /** Mouse flight finished (or was abandoned): drop the run so input unblocks. Stale ids are ignored. */
    fun onMouseAnimDone(id: Int) {
        val s = session ?: return
        if (s.mouseRun?.id != id) return
        session = s.copy(mouseRun = null)
        publish()
    }

    // ---------------------------------------------------------------- rewarded grants

    /**
     * A rewarded ad granted +1 booster to the persisted inventory (inventory is the source of truth): mirror the new stock
     * into the running game so the badge and the next tap see it. Safe to call in any state.
     */
    fun onBoosterRewardGranted() {
        viewModelScope.launch {
            val charges = bestEffortResult { getBoosterCharges() } ?: return@launch
            val s = session ?: return@launch
            session = s.copy(state = s.state.copy(charges = charges))
            enqueueSave()
            publish()
        }
    }

    /**
     * Lose-screen rewarded ad earned: continue the SAME level with one life. Only valid while the lose outcome is
     * showing; board, undo history and charges stay. Timer resumes (it was frozen on the loss). Called ONLY after the
     * earned-reward result, never on dismiss.
     */
    fun onRevive() {
        val s = session ?: return
        if (s.outcome !is OutcomeUi.Lost) return
        val revived = reviveGame(s.state) ?: return
        session = s.copy(state = revived, outcome = null, hint = null, hintOverlay = false)
        attempt.revive()
        analytics.log(AnalyticsEvent.LevelRevive(levelContext(s.puzzle), attemptStats(s)))
        resumeTimer()
        enqueueSave() // finish() cleared the autosave on the loss; write the revived game back
        publish()
    }

    // ---------------------------------------------------------------- lifecycle

    /** Screen paused (also: an ad / other Activity covers it) or app stopped: stop the clock and persist. */
    fun onAppStopped() {
        appActive = false
        pauseTimer()
        enqueueSave()
        // Leaving / covering the screen mid-flight: state is already final, just release the input block.
        val s = session
        if (s?.mouseRun != null) {
            session = s.copy(mouseRun = null)
            publish()
        }
    }

    fun onAppStarted() {
        appActive = true
        resumeTimer()
    }

    // ---------------------------------------------------------------- internals

    private fun playing(): Session? = session?.takeIf { it.state.isPlaying && it.outcome == null }

    /** Like [playing] but also false while the mouse flight animation runs (board / booster / undo input blocked). */
    private fun idle(): Session? = playing()?.takeIf { it.mouseRun == null }

    private fun applyStep(before: Session, step: StepResult, defaultHaptic: HapticKind, cell: Pos?, crossSfx: Boolean = true, haptic: Boolean = true) {
        if (step.state === before.state) return // engine no-op

        var hapticKind = defaultHaptic
        if (cell != null && defaultHaptic == HapticKind.TICK &&
            step.state.board[cell.row, cell.col] == CellMark.CAT
        ) {
            hapticKind = HapticKind.PLACE
        }
        // Cat placed by the player (incl. a wrong placement) or by the cat booster; never removal / undo.
        var catPlaced = cell != null && step.state.board[cell.row, cell.col] == CellMark.CAT &&
            before.state.board[cell.row, cell.col] != CellMark.CAT
        var wrongPlaced = false
        var solved = false
        var lost = false
        for (e in step.events) {
            when (e) {
                is GameEvent.WrongPlacement -> {
                    hapticKind = HapticKind.WRONG
                    if (cell != null) {
                        catPlaced = true
                        wrongPlaced = true
                    }
                }
                is GameEvent.Conflict -> Unit
                GameEvent.Solved -> solved = true
                GameEvent.Lost -> lost = true
            }
        }

        // Engine only detects a win right after a cat placement. Removal / undo can also leave a full valid
        // board (e.g. removing the stray wrong cat), so re-check here; skipped when Solved/Lost already fired.
        var nextState = step.state
        if (!solved && !lost && nextState.isPlaying && analyseBoard(before.puzzle, nextState.board).solved) {
            solved = true
            nextState = nextState.copy(status = GameStatus.SOLVED)
        }
        // Clean correct placement: a cat newly placed by the player / cat booster on its solution cell (no
        // WrongPlacement) that is not flagged as a conflict. Never for removal, X marks, undo (cell == null) or load.
        val correct = catPlaced && !wrongPlaced && cell != null &&
            cell.index(before.puzzle.size) !in analyseBoard(before.puzzle, nextState.board).conflictCells
        if (correct) hapticKind = HapticKind.CORRECT // clear placement buzz (TICK is X-mark only)
        session = before.copy(
            state = nextState, hint = null, hintOverlay = false,
            correctPlacement = if (correct && cell != null) CorrectPlacementUi(cell.row, cell.col, ++placementSeq) else before.correctPlacement,
        )
        if (solved || lost) {
            finish(won = solved)
        } else {
            if (haptic) sendHaptic(hapticKind)
        }
        val catLayered = AudioPolicy.layersCatOnPlacement(!isDaily && PieceSkin.forLevel(before.puzzle.levelIndex).isAnimal)
        // After haptic / outcome events so their order is unchanged. Winning placement skips the meow
        // (win / award sound replaces it); a losing wrong placement still plays WRONG; lose has no extra sound.
        when {
            solved -> sendSfx(
                if (!isDaily && OutcomeLogic.headline(before.puzzle.levelIndex) == WinHeadline.STANDARD) {
                    SfxKind.WIN
                } else {
                    SfxKind.AWARD // milestone levels and the daily
                },
            )
            wrongPlaced -> sendSfx(SfxKind.WRONG)
            // Soft chime replaces the meow on a clean correct placement (one placement sound, never both).
            correct -> sendSfx(if (catLayered) SfxKind.CHIME else SfxKind.CHIME_SOLO)
            // Boss levels place animals: never a cat sound on placement (the idle meow is separate).
            catPlaced -> sendSfx(if (catLayered) SfxKind.MEOW else SfxKind.CHIME_SOLO)
            // X mark(s) newly placed by tap or drag (not removals / undo). Cat placement keeps its meow above.
            crossSfx && step.state.board.marks.count { it == CellMark.X } > before.state.board.marks.count { it == CellMark.X } ->
                sendSfx(SfxKind.CROSS)
        }
        enqueueSave()
        publish()
    }

    private fun finish(won: Boolean) {
        val s = session ?: return
        pauseTimer()
        val result = computeScore(s.puzzle, s.state, (elapsedMs() / 1000L).toInt())
        session = s.copy(outcome = GameUiMapper.outcome(result))
        attempt.end() // a finished attempt can never be logged as a quit
        val ctx = levelContext(s.puzzle)
        val stats = attemptStats(s, result.score.total)
        viewModelScope.launch {
            val completion = bestEffortResult { completeLevel(result) }
            analytics.log(
                AnalyticsEvent.LevelEnd(
                    // Replay is only known for sure after the save (first completion or not).
                    ctx = if (result.won && !isDaily && completion != null) ctx.copy(isReplay = !completion.firstCompletion) else ctx,
                    stats = stats,
                    won = result.won,
                    newBestTime = result.won && completion?.isNewBestTime == true,
                    newBestScore = result.won && completion?.isNewBest == true,
                ),
            )
            bestEffort { userProps.sync() }
        }
        sendHaptic(if (won) HapticKind.WIN else HapticKind.LOSE)
        _events.trySend(
            if (won) GameUiEvent.LevelWon(s.puzzle.levelIndex, result.score.total)
            else GameUiEvent.LevelLost(s.puzzle.levelIndex),
        )
    }

    /** Fresh attempt (new level, restart): opens a new attempt and logs `level_start`. */
    private fun logStart(puzzle: Puzzle) {
        attempt.begin(attemptKey(puzzle))
        analytics.log(AnalyticsEvent.LevelStart(levelContext(puzzle)))
    }

    // ---- analytics helpers ----

    /** Session-local attempt counter key: one per level (daily has one key). */
    private fun attemptKey(puzzle: Puzzle): String = if (isDaily) AttemptMode.DAILY else "L${puzzle.levelIndex}"

    private fun levelContext(puzzle: Puzzle) = LevelContext(
        mode = if (isDaily) AttemptMode.DAILY else AttemptMode.LEVELS,
        levelNumber = if (isDaily) null else puzzle.levelIndex,
        world = if (isDaily) null else WorldRules.worldNumber(puzzle.levelIndex),
        boardSize = puzzle.size,
        difficulty = if (isDaily) null else "t${DifficultyCurve.specFor(puzzle.levelIndex).targetTechnique}",
        attemptNo = attempt.attemptNo,
        isReplay = !isDaily && isReplay,
    )

    private fun attemptStats(s: Session, score: Int? = null) = AttemptStats(
        durationSec = (elapsedMs() / 1000L).toInt(),
        livesLost = s.state.heartsLost,
        hintsUsed = s.state.hintsUsed,
        boostersUsed = attempt.boostersUsed,
        score = score,
        resumed = attempt.resumed,
    )

    /** Logs `level_quit` once per open attempt. Never on app background (ads / shop / settings also pause the screen). */
    private fun maybeLogQuit(reason: String) {
        val s = session ?: return
        if (!attempt.consumeQuit()) return
        analytics.log(AnalyticsEvent.LevelQuit(levelContext(s.puzzle), attemptStats(s), reason))
    }

    /** Player left the game screen through an explicit back action. */
    fun onUserLeft(reason: String) = maybeLogQuit(reason)

    override fun onCleared() {
        maybeLogQuit(QuitReason.CLEARED)
        super.onCleared()
    }

    private fun sendHaptic(kind: HapticKind) {
        if (settings.haptics) _events.trySend(GameUiEvent.Haptic(kind))
    }

    private fun sendSfx(kind: SfxKind) {
        if (settings.sfx) _events.trySend(GameUiEvent.Sfx(kind))
    }

    private fun enqueueSave() {
        val s = session ?: return
        saveQueue.trySend(SaveRequest(s.puzzle.levelIndex, s.state, elapsedMs()))
    }

    private fun publish() {
        val s = session ?: return
        val analysis = analyseBoard(s.puzzle, s.state.board)
        val score = computeScore(s.puzzle, s.state, (elapsedMs() / 1000L).toInt()).score.total
        _state.value = GameUiState.Ready(
            GameUiMapper.content(
                s.puzzle, s.state, analysis, s.hint, score, settings, s.outcome,
                daily = isDaily, hintOverlayOpen = s.hintOverlay,
            ).copy(mouseRun = s.mouseRun, lastCorrectPlacement = s.correctPlacement),
        )
    }

    // timer

    private fun elapsedMs(): Long =
        elapsedBaseMs + (runningSinceMs?.let { time.monotonicMillis() - it } ?: 0L)

    private fun pauseTimer() {
        runningSinceMs?.let {
            elapsedBaseMs += time.monotonicMillis() - it
            runningSinceMs = null
        }
    }

    private fun resumeTimer() {
        if (runningSinceMs == null && appActive && playing() != null) {
            runningSinceMs = time.monotonicMillis()
        }
    }

    private suspend inline fun bestEffort(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // persistence hiccups must not break play
        }
    }

    private suspend inline fun <T> bestEffortResult(block: () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }

    companion object {
        /** Nav / SavedStateHandle argument: true = Daily Challenge mode. */
        const val ARG_DAILY = "daily"

        /** Nav / SavedStateHandle argument: level to open (> 0), 0 / absent = resolve the start level. */
        const val ARG_LEVEL = "level"

        /**
         * STREAK RULE SWITCH. false (shipped, needed so streak milestone rewards cannot be farmed by just opening a
         * level) = a day counts only on a WIN (CompleteLevel always records on a win). true = a day also counts when a
         * level is started/resumed.
         */
        const val RECORD_STREAK_ON_LEVEL_START = false
    }
}
