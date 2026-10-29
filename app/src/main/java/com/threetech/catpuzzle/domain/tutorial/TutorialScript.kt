package com.threetech.catpuzzle.domain.tutorial

import com.threetech.catpuzzle.domain.engine.GENERATOR_VERSION
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.Puzzle

/** What the player must do to finish a step. */
enum class ExpectedAction {
    /** Read the card, press the Next / Got it button. */
    CONTINUE,

    /** Single tap on each target cell (marks X). */
    TAP_X,

    /** One swipe (or several) across the target cells (marks X). */
    SWIPE_X,

    /** Double-tap a target cell (places the cat). */
    DOUBLE_TAP_CAT,

    /** Press the hint booster. */
    HINT_BUTTON,

    /** Press the cat booster (places the target cat). */
    CAT_BUTTON,
}

/** Which screen element a step is about; the UI dims everything else. */
enum class TutorialFocus { BOARD, HEARTS, HINT_BUTTON, CAT_BUTTON }

/** Instruction text keys. The UI maps each to a string resource. */
enum class TutorialTextKey {
    PLACE_FIRST,
    ONE_PER_COLOR,
    ROW_COLUMN,
    NO_TOUCH,
    LAST_SPOT,
    HEARTS,
    TRY_HINT,
    HINT_SHOWN,
    TRY_CAT_BOOSTER,
    DONE,
}

/**
 * One scripted step. [targets] are the cells the step is about, in the order the pointer visits them.
 * [showHintRing]: draw the hint target ring on [targets] (the step after the hint button).
 */
data class TutorialStep(
    val text: TutorialTextKey,
    val expected: ExpectedAction,
    val targets: List<Pos> = emptyList(),
    val focus: TutorialFocus = TutorialFocus.BOARD,
    val showHintRing: Boolean = false,
)

/**
 * Hand-authored tutorial: a fixed 4x4 board plus the scripted steps. No generator, no persistence,
 * NO starter cat. Pure data.
 *
 * Board (region id per cell), solution cols per row 1,3,0,2:
 *  2 0 1 1
 *  2 1 1 1
 *  2 2 3 1
 *  2 3 3 3
 * Region 0 is a single cell (0,1): the first cat is obvious. Solved by naked singles only.
 */
object TutorialScript {
    const val SIZE = 4

    val REGIONS: IntArray = intArrayOf(
        2, 0, 1, 1,
        2, 1, 1, 1,
        2, 2, 3, 1,
        2, 3, 3, 3,
    )

    val SOLUTION: IntArray = intArrayOf(1, 3, 0, 2)

    /** Level index 0 = "not a real level" (real levels are 1-based). Never stored anywhere. */
    val puzzle: Puzzle = Puzzle(
        size = SIZE,
        regions = REGIONS,
        solution = SOLUTION,
        seed = 0L,
        levelIndex = 0,
        techniqueLevel = 1,
        generatorVersion = GENERATOR_VERSION,
    )

    /** Hint / cat booster charges the tutorial player starts with (display only, never persisted). */
    const val BOOSTER_CHARGES = 1

    val steps: List<TutorialStep> = listOf(
        // 0: the single-cell color has to hold its cat.
        TutorialStep(TutorialTextKey.PLACE_FIRST, ExpectedAction.DOUBLE_TAP_CAT, listOf(Pos(0, 1))),
        // 1: rule recap.
        TutorialStep(TutorialTextKey.ONE_PER_COLOR, ExpectedAction.CONTINUE),
        // 2: the cat's row is out.
        TutorialStep(
            TutorialTextKey.ROW_COLUMN,
            ExpectedAction.TAP_X,
            listOf(Pos(0, 0), Pos(0, 2), Pos(0, 3)),
        ),
        // 3: the cat's neighbours are out.
        TutorialStep(
            TutorialTextKey.NO_TOUCH,
            ExpectedAction.SWIPE_X,
            listOf(Pos(1, 0), Pos(1, 1), Pos(1, 2)),
        ),
        // 4: only one cell left in row 1.
        TutorialStep(TutorialTextKey.LAST_SPOT, ExpectedAction.DOUBLE_TAP_CAT, listOf(Pos(1, 3))),
        // 5: hearts.
        TutorialStep(TutorialTextKey.HEARTS, ExpectedAction.CONTINUE, focus = TutorialFocus.HEARTS),
        // 6: hint booster.
        TutorialStep(TutorialTextKey.TRY_HINT, ExpectedAction.HINT_BUTTON, focus = TutorialFocus.HINT_BUTTON),
        // 7: the hint points at the next cat.
        TutorialStep(
            TutorialTextKey.HINT_SHOWN,
            ExpectedAction.DOUBLE_TAP_CAT,
            listOf(Pos(2, 0)),
            showHintRing = true,
        ),
        // 8: cat booster places the last cat.
        TutorialStep(
            TutorialTextKey.TRY_CAT_BOOSTER,
            ExpectedAction.CAT_BUTTON,
            listOf(Pos(3, 2)),
            focus = TutorialFocus.CAT_BUTTON,
        ),
        // 9: done.
        TutorialStep(TutorialTextKey.DONE, ExpectedAction.CONTINUE),
    )
}
