package com.threetech.catpuzzle.ui.screens.tutorial

import com.threetech.catpuzzle.domain.engine.BoardValidator
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.Hint
import com.threetech.catpuzzle.domain.model.HintTechnique
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.Settings
import com.threetech.catpuzzle.domain.tutorial.ExpectedAction
import com.threetech.catpuzzle.domain.tutorial.TutorialEngine
import com.threetech.catpuzzle.domain.tutorial.TutorialScript
import com.threetech.catpuzzle.domain.tutorial.TutorialState
import com.threetech.catpuzzle.ui.screens.game.GameUiMapper

/** Tutorial domain state -> UI model. Pure, no Android. */
object TutorialUiMapper {

    fun map(engine: TutorialEngine, state: TutorialState, settings: Settings): TutorialUiState {
        val puzzle = TutorialScript.puzzle
        val step = engine.step(state)
        val remaining = engine.remainingTargets(state)
        val analysis = BoardValidator.analyse(puzzle, state.game.board)
        val hint: Hint? = if (step.showHintRing && remaining.isNotEmpty()) {
            Hint.Place(remaining.first(), HintTechnique.REVEAL)
        } else {
            null
        }
        val isLast = state.stepIndex >= engine.stepCount - 1
        return TutorialUiState(
            stepIndex = state.stepIndex,
            stepCount = engine.stepCount,
            text = step.text,
            notice = state.notice,
            board = GameUiMapper.board(puzzle, state.game, analysis, hint),
            spotlight = step.targets.map { it.index(puzzle.size) }.toSet(),
            pointer = pointer(step.expected, remaining),
            focus = step.focus,
            hearts = state.game.hearts,
            maxHearts = GameState.MAX_HEARTS,
            hintCharges = state.game.chargesOf(Booster.HINT),
            catCharges = state.game.chargesOf(Booster.CAT),
            showContinue = step.expected == ExpectedAction.CONTINUE,
            continueIsFinal = step.expected == ExpectedAction.CONTINUE && isLast,
            rejectCount = state.rejections,
            patternMode = settings.patternMode,
        )
    }

    fun pointer(expected: ExpectedAction, remaining: List<Pos>): TutorialPointerUi? =
        when (expected) {
            ExpectedAction.CONTINUE -> null
            ExpectedAction.TAP_X ->
                remaining.firstOrNull()?.let { TutorialPointerUi(PointerKind.TAP, it, it) }
            ExpectedAction.DOUBLE_TAP_CAT ->
                remaining.firstOrNull()?.let { TutorialPointerUi(PointerKind.DOUBLE_TAP, it, it) }
            ExpectedAction.SWIPE_X ->
                if (remaining.isEmpty()) null else TutorialPointerUi(PointerKind.SWIPE, remaining.first(), remaining.last())
            ExpectedAction.HINT_BUTTON, ExpectedAction.CAT_BUTTON ->
                TutorialPointerUi(PointerKind.TAP, null, null)
        }
}
