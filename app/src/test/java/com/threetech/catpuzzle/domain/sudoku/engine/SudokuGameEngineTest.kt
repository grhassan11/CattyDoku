package com.threetech.catpuzzle.domain.sudoku.engine

import com.threetech.catpuzzle.domain.sudoku.model.SudokuGameState
import com.threetech.catpuzzle.domain.sudoku.model.SudokuHint
import com.threetech.catpuzzle.domain.sudoku.model.SudokuPuzzle
import com.threetech.catpuzzle.domain.sudoku.model.SudokuStatus
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTechnique
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTier
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuGameEngineTest {
    private val E = SudokuGameEngine

    // 4x4 solved grid; empties at 0 (1), 1 (2), 5 (4), 10 (4), 15 (1).
    private val solution = intArrayOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 1, 4, 3, 4, 3, 2, 1)
    private val empties = intArrayOf(0, 1, 5, 10, 15)

    private fun puzzle(): SudokuPuzzle {
        val givens = solution.copyOf()
        for (c in empties) givens[c] = 0
        return SudokuPuzzle(SudokuTier.EASY, givens, solution, 7L, SUDOKU_GENERATOR_VERSION, SudokuTechnique.NAKED_SINGLE, true)
    }

    private fun fresh() = E.newGame(puzzle())

    private fun SudokuGameState.at(cell: Int) = E.select(this, cell)
    private fun SudokuGameState.put(d: Int, el: Long = elapsedMs) = E.enterDigit(this, d, el)

    @Test
    fun newGameStartsFromGivens() {
        val s = fresh()
        assertArrayEquals(puzzle().givens, s.values)
        assertEquals(3, s.hearts)
        assertEquals(SudokuStatus.PLAYING, s.status)
        assertTrue(s.notes.all { it == 0 })
    }

    @Test
    fun correctDigitScoresAndLocks() {
        val s = fresh().at(0).put(1)
        assertEquals(1, s.values[0])
        assertEquals(SudokuScoreCalculator.digitPoints(SudokuTier.EASY), s.score)
        assertEquals(3, s.hearts)
        assertSame(s, s.put(1))
        assertSame(s, E.erase(s))
        assertSame(s, s.put(2))
    }

    @Test
    fun givensAreImmutable() {
        val s = fresh().at(2)
        assertSame(s, s.put(1))
        assertSame(s, E.erase(s))
    }

    @Test
    fun mistakeCostsHeartAndPenaltyAndStaysVisible() {
        val ok = fresh().at(0).put(1).at(1)
        val s = ok.put(3)
        assertEquals(3, s.values[1])
        assertEquals(2, s.hearts)
        assertEquals(1, s.mistakes)
        assertEquals((ok.score - SudokuScoreCalculator.MISTAKE_PENALTY).coerceAtLeast(0), s.score)
        val cleared = E.erase(s)
        assertEquals(0, cleared.values[1])
        assertEquals(2, cleared.hearts)
        // same wrong digit again on the same cell is ignored
        assertSame(s, s.put(3))
    }

    @Test
    fun scoreNeverGoesNegative() {
        val s = fresh().at(0).put(2)
        assertEquals(0, s.score)
    }

    @Test
    fun wrongDigitCanBeReplacedByCorrectOneWithoutExtraMistake() {
        val s = fresh().at(0).put(2).put(1)
        assertEquals(1, s.values[0])
        assertEquals(1, s.mistakes)
        assertEquals(2, s.hearts)
    }

    @Test
    fun threeMistakesIsGameOver() {
        var s = fresh().at(0).put(2)
        assertEquals(SudokuStatus.PLAYING, s.status)
        s = s.put(3)
        assertEquals(SudokuStatus.PLAYING, s.status)
        s = s.put(4)
        assertEquals(0, s.hearts)
        assertEquals(SudokuStatus.GAME_OVER, s.status)
        assertSame(s, s.put(1))
        assertSame(s, E.undo(s))
        assertSame(s, E.erase(s))
    }

    @Test
    fun notesToggleAndNoNotesOnValueCells() {
        val s = E.toggleNotesMode(fresh()).at(1).put(1).put(2)
        assertEquals(0b11, s.notes[1])
        val off = s.put(1)
        assertEquals(0b10, off.notes[1])
        // given cell takes no notes
        val onGiven = off.at(2)
        assertSame(onGiven, onGiven.put(1))
        // filled cell takes no notes
        val filled = E.toggleNotesMode(fresh().at(0).put(1)) // notes mode on, cell 0 holds a correct digit
        assertSame(filled, filled.put(2))
    }

    @Test
    fun correctPlacementClearsPeerNotes() {
        // cell 0 peers (4x4): row 1,2,3; col 4,8,12; box 5.
        var s = E.toggleNotesMode(fresh())
        s = s.at(1).put(1).put(2) // notes {1,2} on cell 1 (row peer)
        s = s.at(5).put(1).put(3) // notes {1,3} on cell 5 (box peer)
        s = s.at(10).put(1) //       note {1} on cell 10 (NOT a peer of 0)
        s = E.toggleNotesMode(s).at(0).put(1)
        assertEquals(0b10, s.notes[1])
        assertEquals(0b100, s.notes[5])
        assertEquals(0b1, s.notes[10])
        assertEquals(0, s.notes[0])
    }

    @Test
    fun undoRestoresValueNotesAndTakesBackPoints() {
        var s = E.toggleNotesMode(fresh()).at(1).put(1)
        s = E.toggleNotesMode(s).at(0).put(1)
        assertEquals(0, s.notes[1]) // auto-cleared
        assertEquals(10, s.score)
        val u = E.undo(s)
        assertEquals(0, u.values[0])
        assertEquals(0b1, u.notes[1])
        assertEquals(0, u.score)
        assertEquals(0, u.selected)
    }

    @Test
    fun undoDoesNotRefundHeartsAndIsUnlimited() {
        var s = fresh().at(0).put(2)
        assertEquals(2, s.hearts)
        s = E.undo(s)
        assertEquals(0, s.values[0])
        assertEquals(2, s.hearts)
        assertEquals(1, s.mistakes)
        // unlimited: 30 note toggles, undo all
        var n = E.toggleNotesMode(fresh()).at(1)
        repeat(30) { n = n.put(if (it % 2 == 0) 1 else 2) }
        assertEquals(30, n.undo.size)
        repeat(30) { n = E.undo(n) }
        assertTrue(n.undo.isEmpty())
        assertEquals(0, n.notes[1])
        assertSame(n, E.undo(n))
    }

    @Test
    fun hintRevealsDigitWithoutScoreOrHeartAndCountsHint() {
        val s = fresh().at(15)
        val hint = SudokuHintFinder.find(s.size, solution, s.values, s.selected)!!
        assertEquals(15, hint.cell)
        val h = E.applyHint(s, hint)
        assertEquals(1, h.values[15])
        assertEquals(1, h.hintsUsed)
        assertEquals(0, h.score)
        assertEquals(3, h.hearts)
        // undo takes the digit back but the hint stays counted
        val u = E.undo(h)
        assertEquals(0, u.values[15])
        assertEquals(1, u.hintsUsed)
    }

    @Test
    fun hintOverWrongDigitFixesItWithoutMistake() {
        val s = fresh().at(0).put(2)
        val h = E.applyHint(s, SudokuHint(0, 1, SudokuTechnique.GUESS))
        assertEquals(1, h.values[0])
        assertEquals(1, h.mistakes)
        assertEquals(1, h.hintsUsed)
    }

    @Test
    fun hintWithWrongDigitIsRejected() {
        val s = fresh().at(0)
        assertSame(s, E.applyHint(s, SudokuHint(0, 3, SudokuTechnique.GUESS)))
        assertSame(s, E.applyHint(s, SudokuHint(2, 3, SudokuTechnique.GUESS))) // given cell
    }

    @Test
    fun solvingWinsAndAddsTimeBonusOnce() {
        var s = fresh()
        for ((i, c) in empties.withIndex()) {
            val last = i == empties.lastIndex
            s = s.at(c).put(solution[c], if (last) 30_000L else 0L)
        }
        assertEquals(SudokuStatus.SOLVED, s.status)
        val expected = empties.size * SudokuScoreCalculator.digitPoints(SudokuTier.EASY) +
            SudokuScoreCalculator.timeBonus(SudokuTier.EASY, 30_000L)
        assertEquals(expected, s.score)
        assertEquals(30_000L, s.elapsedMs)
        // no more input / undo after the win
        assertSame(s, s.put(1))
        assertSame(s, E.undo(s))
    }

    @Test
    fun hintCanFinishThePuzzle() {
        var s = fresh()
        for (c in empties.dropLast(1)) s = s.at(c).put(solution[c])
        s = E.applyHint(s.at(15), SudokuHint(15, 1, SudokuTechnique.NAKED_SINGLE), 10_000L)
        assertEquals(SudokuStatus.SOLVED, s.status)
        assertEquals(1, s.hintsUsed)
    }

    @Test
    fun pauseBlocksInputAndClockIsMonotonic() {
        val s = fresh().at(0)
        val p = E.pause(s, 5_000L)
        assertTrue(p.paused)
        assertEquals(5_000L, p.elapsedMs)
        assertSame(p, p.put(1))
        assertSame(p, E.select(p, 1))
        assertSame(p, E.tick(p, 9_000L))
        val r = E.resume(p)
        assertFalse(r.paused)
        assertEquals(5_000L, E.tick(r, 3_000L).elapsedMs) // going backwards is ignored
        assertEquals(8_000L, E.tick(r, 8_000L).elapsedMs)
        // a smaller elapsed passed to an action never rewinds the clock
        assertEquals(5_000L, r.put(1, 100L).elapsedMs)
    }

    @Test
    fun restartResetsEverythingButPuzzle() {
        val s = fresh().at(0).put(2).put(1)
        val r = E.restart(s)
        assertArrayEquals(puzzle().givens, r.values)
        assertEquals(3, r.hearts)
        assertEquals(0, r.mistakes)
        assertEquals(0, r.score)
        assertEquals(0L, r.elapsedMs)
        assertTrue(r.undo.isEmpty())
        assertSame(s.puzzle, r.puzzle)
    }

    @Test
    fun normalizeHealsSolvedAndDeadSaves() {
        val solved = fresh().copy(values = solution.copyOf())
        assertEquals(SudokuStatus.SOLVED, E.normalize(solved).status)
        val dead = fresh().copy(hearts = 0)
        assertEquals(SudokuStatus.GAME_OVER, E.normalize(dead).status)
        val ok = fresh()
        assertSame(ok, E.normalize(ok))
    }

    @Test
    fun outOfRangeInputIsIgnored() {
        val s = fresh()
        assertSame(s, E.select(s, -1))
        assertSame(s, E.select(s, 16))
        val a = s.at(0)
        assertSame(a, a.put(0))
        assertSame(a, a.put(5)) // 4x4 has digits 1..4 only
        assertSame(s, s.put(1)) // nothing selected
    }
}
