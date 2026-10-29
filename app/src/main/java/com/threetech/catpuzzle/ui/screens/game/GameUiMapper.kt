package com.threetech.catpuzzle.ui.screens.game

import com.threetech.catpuzzle.domain.engine.CatVariants
import com.threetech.catpuzzle.domain.engine.DailyRules
import com.threetech.catpuzzle.domain.engine.MouseBoosterPolicy
import com.threetech.catpuzzle.domain.model.Analysis
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.GameResult
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.Hint
import com.threetech.catpuzzle.domain.model.HintTechnique
import com.threetech.catpuzzle.domain.model.MistakeKind
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.Puzzle
import com.threetech.catpuzzle.domain.model.Settings

/** Domain -> UI model mapping. Pure, no Android. */
object GameUiMapper {

    /** Settings gear red dot. Always on for now (reference screenshots); flip here when real rules exist. */
    const val SETTINGS_BADGE_ALWAYS_ON = true

    fun content(
        puzzle: Puzzle,
        state: GameState,
        analysis: Analysis,
        hint: Hint?,
        score: Int,
        settings: Settings,
        outcome: OutcomeUi?,
        daily: Boolean = false,
        hintOverlayOpen: Boolean = false,
    ): GameContentUi = GameContentUi(
        levelNumber = if (daily) 0 else puzzle.levelIndex,
        score = score,
        hearts = state.hearts,
        maxHearts = GameState.MAX_HEARTS,
        board = board(puzzle, state, analysis, hint),
        regions = catVariants(puzzle).let { v -> List(puzzle.size) { RegionStatusUi(it, analysis.regionDone[it], v[it]) } },
        boosters = BoostersUi(
            catCharges = state.chargesOf(Booster.CAT),
            hintCharges = state.chargesOf(Booster.HINT),
            thirdLocked = daily || !MouseBoosterPolicy.isUnlocked(puzzle.levelIndex),
            thirdUnlockLevel = MouseBoosterPolicy.UNLOCK_LEVEL,
            mouseCharges = state.chargesOf(Booster.MOUSE),
        ),
        hint = hint?.let { hintUi(it) },
        hintOverlay = if (hintOverlayOpen) hintOverlay(puzzle.size, hint) else null,
        canUndo = state.isPlaying && state.undoStack.isNotEmpty(),
        patternMode = settings.patternMode,
        settings = SettingsUi(
            music = settings.music,
            sfx = settings.sfx,
            voice = settings.voice,
            haptics = settings.haptics,
            patternMode = settings.patternMode,
            streakReminders = settings.streakReminders,
            dailyReminders = settings.dailyReminders,
        ),
        settingsBadge = SETTINGS_BADGE_ALWAYS_ON,
        outcome = outcome,
        isDaily = daily,
        dailyEpochDay = if (daily) DailyRules.dayForKey(puzzle.levelIndex) else null,
    )

    fun board(puzzle: Puzzle, state: GameState, analysis: Analysis, hint: Hint?): BoardUi {
        val n = puzzle.size
        val roles = hintRoles(n, hint)
        val variants = catVariants(puzzle)
        val cells = List(n * n) { i ->
            CellUi(
                row = i / n,
                col = i % n,
                region = puzzle.regions[i],
                mark = mark(state.board.marks[i]),
                conflict = i in analysis.conflictCells,
                locked = i in state.lockedCells,
                hintRole = roles[i] ?: HintRole.NONE,
                catVariant = variants[puzzle.regions[i]],
            )
        }
        return BoardUi(n, cells)
    }

    /** Region id -> cat variant; pure function of the puzzle identity. */
    fun catVariants(puzzle: Puzzle): IntArray =
        CatVariants.forRegions(puzzle.seed, puzzle.levelIndex, puzzle.size)

    fun mark(m: CellMark): CellMarkUi = when (m) {
        CellMark.EMPTY -> CellMarkUi.EMPTY
        CellMark.X -> CellMarkUi.X
        CellMark.CAT -> CellMarkUi.CAT
    }

    fun hintUi(hint: Hint): HintUi? = when (hint) {
        Hint.None -> null
        is Hint.Mistake -> HintUi(
            if (hint.kind == MistakeKind.WRONG_CAT) HintMessage.WRONG_CAT else HintMessage.X_ON_SOLUTION,
        )
        is Hint.Place -> HintUi(
            if (hint.technique == HintTechnique.REVEAL) HintMessage.PLACE_REVEAL else HintMessage.PLACE_SINGLE,
            hint.rule,
        )
        is Hint.Eliminate -> HintUi(
            when (hint.technique) {
                HintTechnique.CONFINEMENT -> HintMessage.ELIMINATE_CONFINEMENT
                HintTechnique.LOOKAHEAD -> HintMessage.ELIMINATE_LOOKAHEAD
                else -> HintMessage.ELIMINATE_CROWDING
            },
            hint.rule,
        )
    }

    /** Dim overlay model: the rule/explanation plus the cells that stay lit. Null when there is no hint. */
    fun hintOverlay(size: Int, hint: Hint?): HintOverlayUi? {
        val ui = hint?.let { hintUi(it) } ?: return null
        return HintOverlayUi(ui, hintSpotlight(size, hint))
    }

    /** Flat indices of every cell involved: unit cells, reasons and targets. */
    fun hintSpotlight(size: Int, hint: Hint?): Set<Int> {
        val out = HashSet<Int>()
        fun add(p: Pos) = out.add(p.index(size))
        when (hint) {
            null, Hint.None -> Unit
            is Hint.Mistake -> add(hint.cell)
            is Hint.Place -> {
                hint.focus.forEach(::add)
                add(hint.target)
            }
            is Hint.Eliminate -> {
                hint.focus.forEach(::add)
                hint.reasons.forEach(::add)
                hint.targets.forEach(::add)
            }
        }
        return out
    }

    /** Flat index -> role. Targets win over reasons when a cell is both. */
    fun hintRoles(size: Int, hint: Hint?): Map<Int, HintRole> {
        val out = HashMap<Int, HintRole>()
        fun idx(p: Pos) = p.index(size)
        when (hint) {
            null, Hint.None -> Unit
            is Hint.Mistake -> out[idx(hint.cell)] = HintRole.TARGET
            is Hint.Place -> out[idx(hint.target)] = HintRole.TARGET
            is Hint.Eliminate -> {
                hint.reasons.forEach { out[idx(it)] = HintRole.REASON }
                hint.targets.forEach { out[idx(it)] = HintRole.TARGET }
            }
        }
        return out
    }

    fun outcome(result: GameResult): OutcomeUi =
        if (result.won) {
            OutcomeUi.Won(result.score.total, result.elapsedSeconds, result.heartsLeft, result.hintsUsed)
        } else {
            OutcomeUi.Lost(result.score.total)
        }
}
