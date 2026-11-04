package com.tm.meow.sudoku.puzzle.domain.tutorial

import com.tm.meow.sudoku.puzzle.domain.engine.BoardValidator
import com.tm.meow.sudoku.puzzle.domain.engine.Fixtures
import com.tm.meow.sudoku.puzzle.domain.engine.SolutionCounter
import com.tm.meow.sudoku.puzzle.domain.model.CellMark
import com.tm.meow.sudoku.puzzle.domain.model.GameState
import com.tm.meow.sudoku.puzzle.domain.model.GameStatus
import com.tm.meow.sudoku.puzzle.domain.model.Pos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TutorialScriptTest {

    private val puzzle = TutorialScript.puzzle
    private val steps = TutorialScript.steps
    private val n = TutorialScript.SIZE

    @Test
    fun boardIsFourByFourWithContiguousRegionsAndOneRegionPerRow() {
        assertEquals(4, puzzle.size)
        assertEquals(16, puzzle.regions.size)
        assertTrue(Fixtures.regionsContiguous(4, puzzle.regions))
        assertEquals(setOf(0, 1, 2, 3), puzzle.regions.toSet())
    }

    @Test
    fun puzzleHasExactlyOneSolutionAndItMatchesTheStoredOne() {
        val r = SolutionCounter.count(4, TutorialScript.REGIONS)
        assertEquals(SolutionCounter.Outcome.UNIQUE, r.outcome)
        assertEquals(1, r.solutionCount)
        assertTrue(r.solutions.single().contentEquals(TutorialScript.SOLUTION))
    }

    @Test
    fun storedSolutionSatisfiesEveryRule() {
        val sol = TutorialScript.SOLUTION
        // one cat per row (by construction), one per column
        assertEquals(4, sol.toSet().size)
        // one per region
        assertEquals(4, (0 until 4).map { puzzle.regionAt(it, sol[it]) }.toSet().size)
        // no touching, diagonals included (only adjacent rows can touch)
        for (r in 0 until 3) assertTrue("rows $r/${r + 1} touch", abs(sol[r] - sol[r + 1]) > 1)
        assertTrue(BoardValidator.analyse(puzzle, Fixtures.solutionBoard(4, sol)).solved)
    }

    @Test
    fun scriptStartsWithNoStarterCatAndFullHearts() {
        val s = TutorialState()
        assertEquals(0, s.game.board.catCount)
        assertEquals(GameState.MAX_HEARTS, s.game.hearts)
        assertTrue(s.game.lockedCells.isEmpty())
    }

    @Test
    fun everyTargetIsInBounds() {
        for (step in steps) for (t in step.targets) {
            assertTrue("$t", t.row in 0 until n && t.col in 0 until n)
        }
    }

    @Test
    fun catTargetsAreSolutionCellsAndXTargetsAreNot() {
        for (step in steps) {
            val onSolution = step.targets.map { puzzle.solution[it.row] == it.col }
            when (step.expected) {
                ExpectedAction.DOUBLE_TAP_CAT, ExpectedAction.HINT_PILL ->
                    assertTrue("${step.text}: cat target must be a solution cell", onSolution.all { it })
                ExpectedAction.TAP_X, ExpectedAction.SWIPE_X ->
                    assertTrue("${step.text}: X target must not be a solution cell", onSolution.none { it })
                else -> assertTrue(step.targets.isEmpty())
            }
        }
    }

    @Test
    fun swipeTargetsFormAChainOfTouchingCells() {
        val swipe = steps.single { it.expected == ExpectedAction.SWIPE_X }.targets
        assertTrue(swipe.size >= 2)
        for (i in 0 until swipe.lastIndex) {
            val a = swipe[i]
            val b = swipe[i + 1]
            assertEquals("$a -> $b", 1, abs(a.row - b.row) + abs(a.col - b.col))
        }
    }

    @Test
    fun scriptCoversEveryTeachingGoal() {
        val expected = steps.map { it.expected }.toSet()
        assertEquals(ExpectedAction.entries.toSet(), expected)
        assertEquals(TutorialTextKey.entries.toSet(), steps.map { it.text }.toSet())
        assertEquals(TutorialBottomKey.entries.toSet(), steps.mapNotNull { it.bottom }.toSet())
    }

    @Test
    fun lightFlagsSkipAndTintMatchTheReference() {
        assertEquals(listOf(1, 8, 9), steps.indices.filter { steps[it].light })
        assertEquals(listOf(0), steps.indices.filter { steps[it].skippable })
        assertEquals(TutorialScript.REGION_ROSE, steps[3].tintRegion)
        assertEquals(TutorialScript.REGION_BLUE, steps[5].tintRegion)
        // the tinted cards name a colour that really is the remaining cell's region
        assertEquals(TutorialScript.REGION_ROSE, puzzle.regionAt(3, 1))
        assertEquals(TutorialScript.REGION_BLUE, puzzle.regionAt(1, 0))
        // find-the-last-cat step: target not ringed
        assertFalse(steps[8].spotlight)
        assertEquals(Pos(2, 3), steps[8].targets.single())
    }

    @Test
    fun everyCatIsPlacedBySomeStepAndEveryCellEndsUpMarked() {
        val engine = TutorialEngine()
        val s = TutorialPlayer.playUntil(engine, steps.lastIndex)
        assertTrue(s.game.board.marks.none { it == CellMark.EMPTY })
    }

    @Test
    fun correctPlaythroughSolvesTheBoardWithoutLosingAHeart() {
        val engine = TutorialEngine()
        var s = TutorialState()
        var guard = 0
        while (!s.completed) {
            check(guard++ < 100) { "script does not terminate" }
            val before = s.stepIndex
            s = TutorialPlayer.playStep(engine, s)
            assertEquals(GameState.MAX_HEARTS, s.game.hearts)
            assertEquals(0, s.rejections)
            assertTrue(s.completed || s.stepIndex == before + 1)
        }
        assertEquals(GameStatus.SOLVED, s.game.status)
        assertEquals(4, s.game.board.catCount)
        for (r in 0 until 4) assertEquals(CellMark.CAT, s.game.board[r, TutorialScript.SOLUTION[r]])
    }

    @Test
    fun boardNeverHoldsAConflictAlongTheWay() {
        val engine = TutorialEngine()
        var s = TutorialState()
        while (!s.completed) {
            s = TutorialPlayer.playStep(engine, s)
            assertFalse(BoardValidator.analyse(puzzle, s.game.board).hasConflicts)
        }
    }

}
