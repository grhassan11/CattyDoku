package com.tm.meow.sudoku.puzzle.domain.sudoku.tutorial

import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuGeometry
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuSize
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every check runs for all four sizes (4x4, 6x6, 8x8, 9x9). */
class SudokuTutorialTest {

    private val script = SudokuTutorialScript
    private val sizes = SudokuSize.entries

    /** Board states in play order: the values each step starts from (steps applied correctly). */
    private fun statesPerStep(size: SudokuSize, lessonIndex: Int): List<Pair<SudokuTutorialStep, List<Int>>> {
        val lesson = script.lesson(size, lessonIndex)
        var values = lesson.initial
        return lesson.steps.map { step ->
            val at = step to values
            values = values.toMutableList().also { it[step.cell] = step.digit }
            at
        }
    }

    @Test
    fun solutionIsAValidGridForEverySize() {
        for (size in sizes) {
            val solution = script.solution(size)
            assertEquals("$size cells", size.cells, solution.size)
            assertTrue("$size digits 1..n", solution.all { it in 1..size.n })
        }
    }

    @Test
    fun blockShapesMatchTheProjectGeometry() {
        assertEquals(2 to 2, SudokuSize.S4.box.rows to SudokuSize.S4.box.cols)
        assertEquals(2 to 3, SudokuSize.S6.box.rows to SudokuSize.S6.box.cols)
        assertEquals(2 to 4, SudokuSize.S8.box.rows to SudokuSize.S8.box.cols)
        assertEquals(3 to 3, SudokuSize.S9.box.rows to SudokuSize.S9.box.cols)
        for (size in sizes) {
            val g = SudokuGeometry.of(size)
            // every block has n cells, thanks to rows * cols == n
            for (u in 2 * size.n until 3 * size.n) assertEquals("$size block $u", size.n, g.units[u].size)
        }
    }

    @Test
    fun everySizeHasTwoLessonsWithThreeRuleStepsAndTwoTricks() {
        assertEquals(2, script.LESSON_COUNT)
        for (size in sizes) {
            assertEquals("$size", 3, script.lesson(size, 1).steps.size)
            assertEquals("$size", listOf(SudokuTutorialKind.ROW, SudokuTutorialKind.COLUMN, SudokuTutorialKind.BLOCK), script.lesson(size, 1).steps.map { it.kind })
            assertEquals("$size", listOf(SudokuTutorialKind.LAST_REMAINING, SudokuTutorialKind.CROSS_HATCH), script.lesson(size, 2).steps.map { it.kind })
        }
    }

    @Test
    fun lessonBoardsOnlyHoldSolutionDigits() {
        for (size in sizes) {
            val solution = script.solution(size)
            for (lesson in script.lessons(size)) {
                assertEquals("$size lesson ${lesson.index}", size.cells, lesson.initial.size)
                lesson.initial.forEachIndexed { i, v ->
                    if (v != 0) assertEquals("$size lesson ${lesson.index} cell $i", solution[i], v)
                }
            }
        }
    }

    @Test
    fun everyStepTargetIsEmptyAndItsDigitIsTheSolution() {
        for (size in sizes) {
            val solution = script.solution(size)
            for (lesson in script.lessons(size)) {
                for ((step, values) in statesPerStep(size, lesson.index)) {
                    assertEquals("$size", 0, values[step.cell])
                    assertEquals("$size", solution[step.cell], step.digit)
                    assertTrue("$size digit range", step.digit in 1..size.n)
                }
            }
        }
    }

    @Test
    fun ruleStepsHaveExactlyOneEmptyCellInTheirUnit() {
        for (size in sizes) {
            for ((step, values) in statesPerStep(size, 1)) {
                val empties = script.unitCells(size, step).filter { values[it] == 0 }
                assertEquals("$size ${step.kind}", listOf(step.cell), empties)
                assertEquals("$size ${step.kind}", size.n, script.unitCells(size, step).size)
            }
        }
    }

    @Test
    fun lessonOneEmptiesAreExactlyTheStepTargets() {
        for (size in sizes) {
            val lesson = script.lesson(size, 1)
            val empties = lesson.initial.indices.filter { lesson.initial[it] == 0 }.toSet()
            assertEquals("$size", lesson.steps.map { it.cell }.toSet(), empties)
        }
    }

    @Test
    fun trickStepsLeaveExactlyOneCandidateCellAndMarkTheRest() {
        for (size in sizes) {
            for ((step, values) in statesPerStep(size, 2)) {
                assertTrue(script.isTrick(step.kind))
                assertEquals("$size ${step.kind}", listOf(step.cell), script.candidates(size, values, step))
                val marks = script.marks(size, values, step)
                assertTrue("$size ${step.kind} marks", marks.isNotEmpty())
                assertFalse(step.cell in marks)
                // every empty block cell is either the target or marked
                val emptyBlock = script.unitCells(size, step).filter { values[it] == 0 }.toSet()
                assertEquals("$size ${step.kind}", emptyBlock, marks + step.cell)
                // blockers hold the digit and are part of the spotlight
                val blockers = script.blockers(size, values, step)
                assertTrue("$size ${step.kind} blockers", blockers.isNotEmpty())
                assertTrue(blockers.all { values[it] == step.digit })
                assertTrue(script.spotlight(size, values, step).containsAll(blockers))
            }
        }
    }

    @Test
    fun trickTargetsAreTheOnlyLegalCellOfTheBlockForTheDigit() {
        // Same guarantee read the other way round: placing the digit anywhere else in the block breaks a row / column / block.
        for (size in sizes) {
            val g = SudokuGeometry.of(size)
            for ((step, values) in statesPerStep(size, 2)) {
                val others = script.unitCells(size, step).filter { it != step.cell && values[it] == 0 }
                for (o in others) assertTrue("$size ${step.kind} cell $o", g.peers[o].any { values[it] == step.digit })
                assertFalse("$size ${step.kind}", g.peers[step.cell].any { values[it] == step.digit })
            }
        }
    }

    @Test
    fun ruleStepsHaveNoMarks() {
        for (size in sizes) {
            for ((step, values) in statesPerStep(size, 1)) assertTrue(script.marks(size, values, step).isEmpty())
        }
    }

    @Test
    fun crossHatchStepNeedsTheDigitPlacedByTheFirstTrick() {
        for (size in sizes) {
            val lesson = script.lesson(size, 2)
            val (first, second) = lesson.steps
            assertEquals("$size same digit", first.digit, second.digit)
            // without the first trick's digit the second target would not be the only candidate
            assertTrue("$size", script.candidates(size, lesson.initial, second).size > 1)
        }
    }

    @Test
    fun themeTierIsTheLowestTierOfTheSize() {
        assertEquals(SudokuTier.EASY, script.themeTier(SudokuSize.S4))
        assertEquals(SudokuTier.MEDIUM, script.themeTier(SudokuSize.S6))
        assertEquals(SudokuTier.HARD, script.themeTier(SudokuSize.S8))
        assertEquals(SudokuTier.MASTER, script.themeTier(SudokuSize.S9))
    }

    // ---- engine

    private fun SudokuTutorialState.playStep(): SudokuTutorialState {
        val step = checkNotNull(currentStep)
        return SudokuTutorialEngine.enterDigit(SudokuTutorialEngine.tapCell(this, step.cell), step.digit)
    }

    @Test
    fun startsOnLessonOneIntroOfNineByNineByDefault() {
        val s = SudokuTutorialEngine.initial()
        assertEquals(SudokuSize.S9, s.size)
        assertEquals(1, s.lesson)
        assertEquals(SudokuTutorialPhase.INTRO, s.phase)
        assertEquals(script.lesson(SudokuSize.S9, 1).initial, s.values)
    }

    @Test
    fun initialUsesTheGivenSize() {
        for (size in sizes) {
            val s = SudokuTutorialEngine.initial(size)
            assertEquals(size, s.size)
            assertEquals(script.lesson(size, 1).initial, s.values)
            assertEquals(3, s.stepCount)
        }
    }

    @Test
    fun inputIsIgnoredBeforeStart() {
        for (size in sizes) {
            val s = SudokuTutorialEngine.initial(size)
            val step = checkNotNull(s.currentStep)
            assertEquals(s, SudokuTutorialEngine.tapCell(s, step.cell))
            assertEquals(s, SudokuTutorialEngine.enterDigit(s, step.digit))
        }
    }

    @Test
    fun wrongCellAndWrongDigitAreIgnored() {
        for (size in sizes) {
            val s = SudokuTutorialEngine.start(SudokuTutorialEngine.initial(size))
            val step = checkNotNull(s.currentStep)
            val wrongCell = (step.cell + 1) % size.cells
            assertEquals(s, SudokuTutorialEngine.tapCell(s, wrongCell))
            assertEquals(s, SudokuTutorialEngine.enterDigit(s, step.digit)) // digit before cell tap
            val tapped = SudokuTutorialEngine.tapCell(s, step.cell)
            assertEquals(SudokuTutorialPhase.ENTER_DIGIT, tapped.phase)
            val wrongDigit = step.digit % size.n + 1
            assertEquals(tapped, SudokuTutorialEngine.enterDigit(tapped, wrongDigit))
            assertEquals(tapped, SudokuTutorialEngine.tapCell(tapped, wrongCell))
        }
    }

    @Test
    fun correctStepPlacesTheDigitAndAdvances() {
        for (size in sizes) {
            val s = SudokuTutorialEngine.start(SudokuTutorialEngine.initial(size))
            val first = checkNotNull(s.currentStep)
            val after = s.playStep()
            assertEquals(first.digit, after.values[first.cell])
            assertEquals(1, after.step)
            assertEquals(SudokuTutorialPhase.TAP_CELL, after.phase)
            assertEquals(1, after.solved)
            assertEquals(0, script.lesson(size, 1).initial[first.cell]) // script data untouched
        }
    }

    @Test
    fun lessonOneCompletesThenLessonTwoThenEndsForEverySize() {
        for (size in sizes) {
            var s = SudokuTutorialEngine.start(SudokuTutorialEngine.initial(size))
            repeat(3) { s = s.playStep() }
            assertEquals(SudokuTutorialPhase.LESSON_COMPLETE, s.phase)
            assertEquals("$size", script.solution(size), s.values) // lesson 1 ends on the full solved grid

            val l2 = checkNotNull(SudokuTutorialEngine.nextLesson(s))
            assertEquals(size, l2.size)
            assertEquals(2, l2.lesson)
            assertEquals(SudokuTutorialPhase.INTRO, l2.phase)
            assertEquals(0, l2.step)
            assertEquals(script.lesson(size, 2).initial, l2.values)

            s = SudokuTutorialEngine.start(l2)
            repeat(2) { s = s.playStep() }
            assertEquals(SudokuTutorialPhase.LESSON_COMPLETE, s.phase)
            assertNull(SudokuTutorialEngine.nextLesson(s))
            assertEquals(5, s.solved)
        }
    }

    @Test
    fun nextLessonOnlyFromLessonComplete() {
        assertNull(SudokuTutorialEngine.nextLesson(SudokuTutorialEngine.initial()))
        assertNotNull(script.lesson(SudokuSize.S4, 2))
    }
}
