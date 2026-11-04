package com.tm.meow.sudoku.puzzle.domain.tutorial

import com.tm.meow.sudoku.puzzle.domain.engine.GameEngine
import com.tm.meow.sudoku.puzzle.domain.model.BoardState
import com.tm.meow.sudoku.puzzle.domain.model.CellMark
import com.tm.meow.sudoku.puzzle.domain.model.GameState
import com.tm.meow.sudoku.puzzle.domain.model.Pos
import com.tm.meow.sudoku.puzzle.domain.model.Puzzle

/** What the player did (UI gestures and buttons, already resolved). */
sealed interface TutorialAction {
    data class Tap(val pos: Pos) : TutorialAction

    data class DoubleTap(val pos: Pos) : TutorialAction

    data class Drag(val path: List<Pos>) : TutorialAction

    /** The "Tap here for a hint" pill: places the last cat for free. */
    data object HintPill : TutorialAction

    /** Got it / Start Game. */
    data object Continue : TutorialAction
}

/** Gentle message shown under the instruction. */
enum class TutorialNotice { WRONG_ACTION, TAP_TWICE }

/**
 * Tutorial play state. [game] is a plain [GameState] (hearts never drop: only solution cells can be
 * cat-placed). Lives only in memory: nothing here is ever saved.
 * [rejections] counts wrong actions (UI shakes on change).
 */
data class TutorialState(
    val stepIndex: Int = 0,
    val game: GameState = GameState.initial(TutorialScript.SIZE),
    val rejections: Int = 0,
    val notice: TutorialNotice? = null,
    val completed: Boolean = false,
)

enum class TutorialOutcome {
    /** Accepted, step not finished yet (more targets left). */
    APPLIED,

    /** Accepted, step finished, next step is current. */
    ADVANCED,

    /** Wrong action: state unchanged except [TutorialState.rejections] / notice. */
    REJECTED,

    /** First tap of a double-tap on the right cell: nothing applied yet. */
    NEEDS_SECOND_TAP,

    /** Finished (last Continue, or skip). */
    COMPLETED,

    /** Nothing to do (already completed). */
    IGNORED,
}

class TutorialResult(val state: TutorialState, val outcome: TutorialOutcome)

/**
 * Pure step machine + validator for [TutorialScript]. Wrong input never costs a heart and never
 * advances. Reuses [GameEngine] for the real board rules.
 */
class TutorialEngine(
    private val engine: GameEngine = GameEngine(),
    private val puzzle: Puzzle = TutorialScript.puzzle,
    private val steps: List<TutorialStep> = TutorialScript.steps,
) {
    val stepCount: Int get() = steps.size

    fun step(state: TutorialState): TutorialStep = steps[state.stepIndex.coerceIn(0, steps.lastIndex)]

    /** Targets of the current step that are not satisfied yet, in script order. */
    fun remainingTargets(state: TutorialState): List<Pos> {
        if (state.completed) return emptyList()
        val s = step(state)
        return s.targets.filterNot { satisfied(s, state.game, it) }
    }

    fun skip(state: TutorialState): TutorialResult =
        TutorialResult(state.copy(completed = true, notice = null), TutorialOutcome.COMPLETED)

    fun apply(state: TutorialState, action: TutorialAction): TutorialResult {
        if (state.completed) return TutorialResult(state, TutorialOutcome.IGNORED)
        val s = step(state)
        return when (s.expected) {
            ExpectedAction.CONTINUE -> when (action) {
                TutorialAction.Continue ->
                    if (state.stepIndex >= steps.lastIndex) {
                        TutorialResult(state.copy(completed = true, notice = null), TutorialOutcome.COMPLETED)
                    } else {
                        advance(state, state.game)
                    }
                else -> reject(state)
            }

            ExpectedAction.TAP_X -> when (action) {
                is TutorialAction.Tap ->
                    if (action.pos in s.targets && state.game.board[action.pos.row, action.pos.col] == CellMark.EMPTY) {
                        progress(state, s, engine.toggleX(puzzle, state.game, action.pos).state)
                    } else {
                        reject(state)
                    }
                else -> reject(state)
            }

            ExpectedAction.SWIPE_X -> when (action) {
                is TutorialAction.Drag -> {
                    val path = action.path
                    val ok = path.size >= 2 &&
                        path.all { it in s.targets } &&
                        state.game.board[path.first().row, path.first().col] == CellMark.EMPTY
                    if (ok) progress(state, s, engine.applyDrag(puzzle, state.game, path).state) else reject(state)
                }
                else -> reject(state)
            }

            ExpectedAction.DOUBLE_TAP_CAT -> when (action) {
                is TutorialAction.Tap ->
                    // The gesture resolver reports the first press of a double-tap as a Tap: do not
                    // apply it (no X under the cat), just wait for the second press.
                    if (action.pos in s.targets && !satisfied(s, state.game, action.pos)) {
                        TutorialResult(
                            state.copy(notice = TutorialNotice.TAP_TWICE),
                            TutorialOutcome.NEEDS_SECOND_TAP,
                        )
                    } else {
                        reject(state)
                    }
                is TutorialAction.DoubleTap ->
                    if (action.pos in s.targets && !satisfied(s, state.game, action.pos)) {
                        progress(state, s, engine.applyDoubleTap(puzzle, state.game, action.pos).state)
                    } else {
                        reject(state)
                    }
                else -> reject(state)
            }

            ExpectedAction.HINT_PILL -> when (action) {
                // Free placement on the one remaining target; no inventory involved.
                TutorialAction.HintPill -> {
                    val target = remainingTargets(state).firstOrNull() ?: return reject(state)
                    progress(state, s, engine.applyDoubleTap(puzzle, state.game, target).state)
                }
                is TutorialAction.Tap ->
                    if (action.pos in s.targets && !satisfied(s, state.game, action.pos)) {
                        TutorialResult(state.copy(notice = TutorialNotice.TAP_TWICE), TutorialOutcome.NEEDS_SECOND_TAP)
                    } else {
                        reject(state)
                    }
                is TutorialAction.DoubleTap ->
                    if (action.pos in s.targets && !satisfied(s, state.game, action.pos)) {
                        progress(state, s, engine.applyDoubleTap(puzzle, state.game, action.pos).state)
                    } else {
                        reject(state)
                    }
                else -> reject(state)
            }
        }
    }

    // ---- internals ----

    private fun satisfied(step: TutorialStep, game: GameState, pos: Pos): Boolean {
        val mark = game.board[pos.row, pos.col]
        return when (step.expected) {
            ExpectedAction.TAP_X, ExpectedAction.SWIPE_X -> mark == CellMark.X
            ExpectedAction.DOUBLE_TAP_CAT, ExpectedAction.HINT_PILL -> mark == CellMark.CAT
            else -> true
        }
    }

    /** Apply [next] game state; advance when every target of [step] is satisfied. */
    private fun progress(state: TutorialState, step: TutorialStep, next: GameState): TutorialResult {
        val done = step.targets.all { satisfied(step, next, it) }
        return if (done) {
            advance(state, next)
        } else {
            TutorialResult(state.copy(game = next, notice = null), TutorialOutcome.APPLIED)
        }
    }

    private fun advance(state: TutorialState, rawNext: GameState): TutorialResult {
        val next = fillCrossesWhenSolved(rawNext)
        val idx = (state.stepIndex + 1).coerceAtMost(steps.lastIndex)
        return TutorialResult(
            state.copy(stepIndex = idx, game = next, notice = null),
            TutorialOutcome.ADVANCED,
        )
    }

    /**
     * Once every cat is on the board (solved), mark each still-empty cell with an X so the finished
     * board looks complete. Existing marks stay. Idempotent. Not an undo entry (tutorial has no undo).
     */
    internal fun fillCrossesWhenSolved(game: GameState): GameState {
        val size = game.board.size
        if (game.board.catCount != size || game.board.marks.none { it == CellMark.EMPTY }) return game
        val marks = game.board.marks.map { if (it == CellMark.EMPTY) CellMark.X else it }
        return game.copy(board = BoardState(size, marks))
    }

    private fun reject(state: TutorialState): TutorialResult = TutorialResult(
        state.copy(rejections = state.rejections + 1, notice = TutorialNotice.WRONG_ACTION),
        TutorialOutcome.REJECTED,
    )
}
