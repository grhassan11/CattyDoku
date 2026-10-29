package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.GameStatus
import com.threetech.catpuzzle.domain.model.Hint
import com.threetech.catpuzzle.domain.model.HintTechnique
import com.threetech.catpuzzle.domain.model.MistakeKind
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.Puzzle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

class HintEngineTest {
    private val hints = HintEngine()
    private val game = GameEngine()

    @Test
    fun techniqueIdsMatchSolverLevels() {
        assertEquals(Technique.NAKED_SINGLE, HintTechnique.NAKED_SINGLE.id)
        assertEquals(Technique.CONFINEMENT, HintTechnique.CONFINEMENT.id)
        assertEquals(Technique.CROWDING_PAIR, HintTechnique.CROWDING_PAIR.id)
        assertEquals(Technique.CROWDING_HALL, HintTechnique.CROWDING_HALL.id)
        assertEquals(Technique.LOOKAHEAD, HintTechnique.LOOKAHEAD.id)
        assertEquals(HintTechnique.LOOKAHEAD, HintTechnique.fromId(5))
    }

    @Test
    fun wrongCatIsFirstMistake() {
        val p = GameFixtures.unique4()
        // (0,0) wrong cat, plus an X on solution cell (0,1): cat mistake wins.
        val b = GameFixtures.withMarks(
            4,
            mapOf(Pos(0, 0) to CellMark.CAT, Pos(0, 1) to CellMark.X),
        )
        assertEquals(Hint.Mistake(Pos(0, 0), MistakeKind.WRONG_CAT), hints.next(p, b))
    }

    @Test
    fun xOnSolutionCellIsMistake() {
        val p = GameFixtures.unique4()
        val b = GameFixtures.withMarks(4, mapOf(Pos(1, 3) to CellMark.X, Pos(0, 0) to CellMark.X))
        assertEquals(Hint.Mistake(Pos(1, 3), MistakeKind.X_ON_SOLUTION), hints.next(p, b))
    }

    @Test
    fun wrongXOnNonSolutionCellIsIgnored() {
        val p = GameFixtures.unique4()
        val b = GameFixtures.withMarks(4, mapOf(Pos(0, 0) to CellMark.X))
        val h = hints.next(p, b) as Hint.Place
        assertEquals(Pos(0, 1), h.target)
        assertEquals(HintTechnique.NAKED_SINGLE, h.technique)
    }

    @Test
    fun nakedSingleOnEmptyBoard() {
        val p = GameFixtures.unique4()
        val h = hints.next(p, BoardState.empty(4)) as Hint.Place
        assertEquals(Pos(0, 1), h.target)
        assertEquals(HintTechnique.NAKED_SINGLE, h.technique)
    }

    @Test
    fun nakedSingleCarriesRuleAndFocusContainingTarget() {
        val p = GameFixtures.unique4()
        val h = hints.next(p, BoardState.empty(4)) as Hint.Place
        assertNotNull(h.rule)
        assertTrue(h.focus.isNotEmpty())
        assertTrue(h.target in h.focus)
    }

    @Test
    fun eliminationCarriesRuleAndFocusCoveringReasonsAndTargets() {
        val p = GameFixtures.confine4()
        val h = hints.next(p, BoardState.empty(4)) as Hint.Eliminate
        assertNotNull(h.rule)
        assertTrue(h.focus.containsAll(h.targets))
        assertTrue(h.focus.containsAll(h.reasons))
    }

    @Test
    fun confinementFixture() {
        val p = GameFixtures.confine4()
        val h = hints.next(p, BoardState.empty(4))
        assertTrue("got $h", h is Hint.Eliminate)
        h as Hint.Eliminate
        assertEquals(HintTechnique.CONFINEMENT, h.technique)
        assertTrue(h.targets.isNotEmpty())
        assertTrue(h.reasons.isNotEmpty())
        for (t in h.targets) assertFalse(GameFixtures.isSolution(p, t))
    }

    @Test
    fun crowdingPairFixture() {
        val p = GameFixtures.crowd4()
        val h = hints.next(p, BoardState.empty(4))
        assertTrue("got $h", h is Hint.Eliminate)
        h as Hint.Eliminate
        assertEquals(HintTechnique.CROWDING_PAIR, h.technique)
        for (t in h.targets) assertFalse(GameFixtures.isSolution(p, t))
    }

    @Test
    fun alreadyMarkedEliminationIsSkipped() {
        val p = GameFixtures.confine4()
        val first = hints.next(p, BoardState.empty(4)) as Hint.Eliminate
        var b = BoardState.empty(4)
        for (t in first.targets) b = b.withMark(t.row, t.col, CellMark.X)
        val second = hints.next(p, b)
        if (second is Hint.Eliminate) {
            for (t in second.targets) assertEquals(CellMark.EMPTY, b[t.row, t.col])
        }
        assertFalse(second is Hint.Mistake)
    }

    @Test
    fun solvedBoardReturnsNone() {
        val p = GameFixtures.unique4()
        assertEquals(Hint.None, hints.next(p, Fixtures.solutionBoard(4, p.solution)))
    }

    @Test
    fun hintsWalkSolvesGeneratedPuzzlesWithoutContradiction() {
        val levels = listOf(1, 3, 5, 8, 12, 13, 19, 24, 25, 28)
        var walked = 0
        for ((i, level) in levels.withIndex()) {
            val res = PuzzleGenerator.generateForLevel(level, 1000L + i)
            if (res !is GenerationResult.Success) continue
            walk(res.puzzle)
            walked++
        }
        assertTrue("walked $walked", walked >= levels.size / 2)
    }

    @Test
    fun lookaheadAppearsOnTechniqueFivePuzzles() {
        var seen = false
        var found = false
        for (s in 1..30) {
            val level = listOf(19, 22, 24)[s % 3]
            val res = PuzzleGenerator.generateForLevel(level, 77L * s)
            if (res !is GenerationResult.Success || res.puzzle.techniqueLevel != Technique.LOOKAHEAD) continue
            found = true
            if (HintTechnique.LOOKAHEAD in walk(res.puzzle)) {
                seen = true
                break
            }
        }
        assumeTrue("no technique-5 puzzle generated", found)
        assertTrue("look-ahead hint never produced", seen)
    }

    @Test
    fun partialBoardsNeverContradictSolution() {
        val rng = SeededRng(2024L)
        for (level in listOf(2, 6, 14, 26)) {
            val res = PuzzleGenerator.generateForLevel(level, 9L)
            if (res !is GenerationResult.Success) continue
            val p = res.puzzle
            val n = p.size
            repeat(25) {
                var b = BoardState.empty(n)
                for (r in 0 until n) {
                    if (rng.nextInt(2) == 0) b = b.withMark(r, p.solution[r], CellMark.CAT)
                }
                // random X marks on non-solution cells only
                for (i in 0 until n * n) {
                    val pos = Pos.fromIndex(i, n)
                    if (b.marks[i] == CellMark.EMPTY && !GameFixtures.isSolution(p, pos) && rng.nextInt(4) == 0) {
                        b = b.withMark(pos.row, pos.col, CellMark.X)
                    }
                }
                when (val h = hints.next(p, b)) {
                    is Hint.Place -> {
                        assertTrue(GameFixtures.isSolution(p, h.target))
                        assertFalse(b[h.target.row, h.target.col] == CellMark.CAT)
                    }
                    is Hint.Eliminate -> for (t in h.targets) {
                        assertFalse(GameFixtures.isSolution(p, t))
                        assertFalse(b[t.row, t.col] == CellMark.X)
                    }
                    is Hint.Mistake -> fail("no mistakes on board: $h")
                    Hint.None -> assertEquals(n, b.catCount)
                }
            }
        }
    }

    /** Follow hints to the end; returns techniques used. Fails on any contradiction. */
    private fun walk(p: Puzzle): Set<HintTechnique> {
        var state = GameState.initial(p.size)
        val used = HashSet<HintTechnique>()
        var guard = 0
        while (state.status == GameStatus.PLAYING) {
            assertTrue("walk too long", guard++ < 600)
            when (val h = hints.next(p, state.board)) {
                is Hint.Place -> {
                    assertTrue(GameFixtures.isSolution(p, h.target))
                    used.add(h.technique)
                    state = game.placeCat(p, state, h.target).state
                }
                is Hint.Eliminate -> {
                    assertTrue(h.reasons.isNotEmpty())
                    assertNotNull(h.technique)
                    used.add(h.technique)
                    for (t in h.targets) {
                        assertFalse(GameFixtures.isSolution(p, t))
                        state = game.toggleX(p, state, t).state
                    }
                }
                is Hint.Mistake -> fail("unexpected mistake $h")
                Hint.None -> fail("None before solved")
            }
        }
        assertEquals(GameStatus.SOLVED, state.status)
        assertEquals(GameState.MAX_HEARTS, state.hearts)
        assertEquals(Hint.None, hints.next(p, state.board))
        return used
    }
}
