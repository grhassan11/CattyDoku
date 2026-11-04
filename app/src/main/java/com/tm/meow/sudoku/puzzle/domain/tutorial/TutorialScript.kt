package com.tm.meow.sudoku.puzzle.domain.tutorial

import com.tm.meow.sudoku.puzzle.domain.engine.GENERATOR_VERSION
import com.tm.meow.sudoku.puzzle.domain.model.Pos
import com.tm.meow.sudoku.puzzle.domain.model.Puzzle

/** What the player must do to finish a step. */
enum class ExpectedAction {
    /** Read the card, press the Got it / Start Game button. */
    CONTINUE,

    /** Single tap on each target cell (marks X). */
    TAP_X,

    /** One swipe (or several) across the target cells (marks X). */
    SWIPE_X,

    /** Double-tap a target cell (places the cat). */
    DOUBLE_TAP_CAT,

    /** Tap the hint pill OR double-tap the target cell: places the cat for free. */
    HINT_PILL,
}

/** Instruction text keys. The UI maps each to a string resource. */
enum class TutorialTextKey {
    PLACE_FIRST,
    ONE_PER_COLOR,
    ROW_COLUMN,
    ROSE_LEFT,
    ADJACENT,
    BLUE_LEFT,
    ROW_DONE,
    FIND_LAST,
    EXCELLENT,
}

/** Text of the second (bottom) card, shown on some dim steps. */
enum class TutorialBottomKey { TAP_EMPTY, SWIPE_ACROSS }

/**
 * One scripted step. [targets] are the cells the player acts on, in the order the pointer visits them.
 * [spotlight]: ring + clear hole on [targets] and dim the rest (false on bright steps and on the
 * find-the-last-cat step, where the target is not marked).
 * [light]: bright background, board on a white card, no dim.
 * [bottom]: second card under the board.
 * [tintRegion]: region id whose colour paints the tinted words (the tilde markers) in the card text.
 * [skippable]: show the Skip tutorial link.
 */
data class TutorialStep(
    val text: TutorialTextKey,
    val expected: ExpectedAction,
    val targets: List<Pos> = emptyList(),
    val spotlight: Boolean = targets.isNotEmpty(),
    val light: Boolean = false,
    val bottom: TutorialBottomKey? = null,
    val tintRegion: Int? = null,
    val skippable: Boolean = false,
)

/**
 * Hand-authored tutorial: a fixed 4x4 board plus the scripted steps. No generator, no persistence,
 * NO starter cat. Pure data.
 *
 * Region ids and the palette colour they get: 0 rose (pink), 1 blue (sky), 2 orange (mustard), 3 green.
 * Board (region id per cell), solution cols per row 2,0,3,1:
 *  1 2 3 2     B O G O
 *  1 2 2 2     B O O O
 *  1 1 0 2     B B R O
 *  1 0 0 2     B R R O
 * Region 3 (green) is the single cell (0,2): the first cat is obvious. Solved by naked singles only.
 */
object TutorialScript {
    const val SIZE = 4

    const val REGION_ROSE = 0
    const val REGION_BLUE = 1

    val REGIONS: IntArray = intArrayOf(
        1, 2, 3, 2,
        1, 2, 2, 2,
        1, 1, 0, 2,
        1, 0, 0, 2,
    )

    val SOLUTION: IntArray = intArrayOf(2, 0, 3, 1)

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

    val steps: List<TutorialStep> = listOf(
        // 0: the single-cell colour has to hold its cat.
        TutorialStep(
            TutorialTextKey.PLACE_FIRST,
            ExpectedAction.DOUBLE_TAP_CAT,
            listOf(Pos(0, 2)),
            skippable = true,
        ),
        // 1: rule recap, bright.
        TutorialStep(TutorialTextKey.ONE_PER_COLOR, ExpectedAction.CONTINUE, light = true),
        // 2: the cat's row and column are out.
        TutorialStep(
            TutorialTextKey.ROW_COLUMN,
            ExpectedAction.TAP_X,
            listOf(Pos(0, 0), Pos(0, 1), Pos(0, 3), Pos(1, 2), Pos(2, 2), Pos(3, 2)),
            bottom = TutorialBottomKey.TAP_EMPTY,
        ),
        // 3: only rose cell left.
        TutorialStep(
            TutorialTextKey.ROSE_LEFT,
            ExpectedAction.DOUBLE_TAP_CAT,
            listOf(Pos(3, 1)),
            tintRegion = TutorialScript.REGION_ROSE,
        ),
        // 4: the neighbours are out.
        TutorialStep(
            TutorialTextKey.ADJACENT,
            ExpectedAction.SWIPE_X,
            // Order = the finger's path: each cell touches the next one.
            listOf(Pos(2, 1), Pos(2, 0), Pos(3, 0)),
            bottom = TutorialBottomKey.SWIPE_ACROSS,
        ),
        // 5: only blue cell left.
        TutorialStep(
            TutorialTextKey.BLUE_LEFT,
            ExpectedAction.DOUBLE_TAP_CAT,
            listOf(Pos(1, 0)),
            tintRegion = TutorialScript.REGION_BLUE,
        ),
        // 6: the cat's row is full.
        TutorialStep(
            TutorialTextKey.ROW_DONE,
            ExpectedAction.TAP_X,
            listOf(Pos(1, 1), Pos(1, 3)),
            bottom = TutorialBottomKey.TAP_EMPTY,
        ),
        // 7: one more X, same texts.
        TutorialStep(
            TutorialTextKey.ROW_DONE,
            ExpectedAction.TAP_X,
            listOf(Pos(3, 3)),
            bottom = TutorialBottomKey.TAP_EMPTY,
        ),
        // 8: find the last cat (hint pill or double-tap), bright, target not marked.
        TutorialStep(
            TutorialTextKey.FIND_LAST,
            ExpectedAction.HINT_PILL,
            listOf(Pos(2, 3)),
            spotlight = false,
            light = true,
        ),
        // 9: done, bright + confetti.
        TutorialStep(TutorialTextKey.EXCELLENT, ExpectedAction.CONTINUE, light = true),
    )
}
