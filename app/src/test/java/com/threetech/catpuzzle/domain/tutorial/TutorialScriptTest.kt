package com.threetech.catpuzzle.domain.tutorial

import com.threetech.catpuzzle.domain.engine.BoardValidator
import com.threetech.catpuzzle.domain.engine.Fixtures
import com.threetech.catpuzzle.domain.engine.SolutionCounter
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.GameStatus
import com.threetech.catpuzzle.domain.model.Pos
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
                ExpectedAction.DOUBLE_TAP_CAT, ExpectedAction.CAT_BUTTON ->
                    assertTrue("${step.text}: cat target must be a solution cell", onSolution.all { it })
                ExpectedAction.TAP_X, ExpectedAction.SWIPE_X ->
                    assertTrue("${step.text}: X target must not be a solution cell", onSolution.none { it })
                else -> assertTrue(step.targets.isEmpty())
            }
        }
    }

    @Test
    fun swipeTargetsFormOneStraightLine() {
        val swipe = steps.single { it.expected == ExpectedAction.SWIPE_X }.targets
        assertTrue(swipe.size >= 2)
        val sameRow = swipe.all { it.row == swipe.first().row }
        val sameCol = swipe.all { it.col == swipe.first().col }
        assertTrue(sameRow || sameCol)
        val sorted = if (sameRow) swipe.map { it.col }.sorted() else swipe.map { it.row }.sorted()
        assertEquals(sorted, sorted.indices.map { sorted.first() + it })
    }

    @Test
    fun scriptCoversEveryTeachingGoal() {
        val expected = steps.map { it.expected }.toSet()
        assertTrue(ExpectedAction.DOUBLE_TAP_CAT in expected)
        assertTrue(ExpectedAction.TAP_X in expected)
        assertTrue(ExpectedAction.SWIPE_X in expected)
        assertTrue(ExpectedAction.HINT_BUTTON in expected)
        assertTrue(ExpectedAction.CAT_BUTTON in expected)
        assertTrue(steps.any { it.focus == TutorialFocus.HEARTS })
        assertEquals(TutorialTextKey.entries.toSet(), steps.map { it.text }.toSet())
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

    @Test
    fun hintRingStepComesRightAfterTheHintButtonStep() {
        val i = steps.indexOfFirst { it.expected == ExpectedAction.HINT_BUTTON }
        assertTrue(steps[i + 1].showHintRing)
        assertEquals(1, steps.count { it.showHintRing })
        assertEquals(Pos(2, 0), steps[i + 1].targets.single())
    }
}
