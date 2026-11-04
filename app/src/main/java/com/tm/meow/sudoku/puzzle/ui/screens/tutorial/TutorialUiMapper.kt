package com.tm.meow.sudoku.puzzle.ui.screens.tutorial

import com.tm.meow.sudoku.puzzle.domain.engine.BoardValidator
import com.tm.meow.sudoku.puzzle.domain.model.Pos
import com.tm.meow.sudoku.puzzle.domain.model.Settings
import com.tm.meow.sudoku.puzzle.domain.tutorial.ExpectedAction
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialEngine
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialScript
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialState
import com.tm.meow.sudoku.puzzle.ui.screens.game.GameUiMapper

/** Tutorial domain state -> UI model. Pure, no Android. */
object TutorialUiMapper {

    fun map(engine: TutorialEngine, state: TutorialState, settings: Settings): TutorialUiState {
        val puzzle = TutorialScript.puzzle
        val step = engine.step(state)
        val remaining = engine.remainingTargets(state)
        val analysis = BoardValidator.analyse(puzzle, state.game.board)
        val isLast = state.stepIndex >= engine.stepCount - 1
        val remainingCells = remaining.map { it.index(puzzle.size) }.toSet()
        return TutorialUiState(
            stepIndex = state.stepIndex,
            stepCount = engine.stepCount,
            text = step.text,
            bottom = step.bottom,
            tintRegion = step.tintRegion,
            notice = state.notice,
            board = GameUiMapper.board(puzzle, state.game, analysis, null),
            spotlight = if (step.spotlight) remainingCells else emptySet(),
            interactive = remainingCells,
            pointer = if (step.spotlight) pointer(step.expected, remaining) else null,
            light = step.light,
            hintPill = step.expected == ExpectedAction.HINT_PILL,
            showContinue = step.expected == ExpectedAction.CONTINUE,
            continueIsFinal = step.expected == ExpectedAction.CONTINUE && isLast,
            showSkip = step.skippable,
            rejectCount = state.rejections,
            patternMode = settings.patternMode,
        )
    }

    fun pointer(expected: ExpectedAction, remaining: List<Pos>): TutorialPointerUi? =
        when (expected) {
            ExpectedAction.CONTINUE, ExpectedAction.HINT_PILL -> null
            ExpectedAction.TAP_X ->
                remaining.firstOrNull()?.let { TutorialPointerUi(PointerKind.TAP, it, it) }
            ExpectedAction.DOUBLE_TAP_CAT ->
                remaining.firstOrNull()?.let { TutorialPointerUi(PointerKind.DOUBLE_TAP, it, it) }
            ExpectedAction.SWIPE_X ->
                if (remaining.isEmpty()) null else TutorialPointerUi(PointerKind.SWIPE, remaining.first(), remaining.last(), remaining)
        }
}
