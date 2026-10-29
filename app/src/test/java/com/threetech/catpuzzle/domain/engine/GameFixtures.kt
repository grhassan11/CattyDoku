package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.Puzzle

/** Puzzle + play-state helpers for phase 2 tests. */
object GameFixtures {

    fun puzzle(size: Int, regions: IntArray, solution: IntArray, level: Int = 1, tech: Int = 1): Puzzle =
        Puzzle(size, regions, solution, 1L, level, tech, GENERATOR_VERSION)

    /** 4x4 naked-single puzzle, solution cols 1,3,0,2. */
    fun unique4(): Puzzle = puzzle(4, Fixtures.UNIQUE_4, Fixtures.UNIQUE_4_SOLUTION)

    /** Confinement-first 4x4; solution found by the logic solver. */
    fun confine4(): Puzzle = fromSolver(Fixtures.CONFINE_4, tech = Technique.CONFINEMENT)

    /** Crowding-pair-first 4x4; solution found by the logic solver. */
    fun crowd4(): Puzzle = fromSolver(Fixtures.CROWD_4, tech = Technique.CROWDING_PAIR)

    private fun fromSolver(regions: IntArray, tech: Int): Puzzle {
        val r = LogicSolver.solve(4, regions)
        check(r.solved) { "fixture not logic-solvable" }
        return puzzle(4, regions, r.cats, tech = tech)
    }

    fun isSolution(p: Puzzle, pos: Pos): Boolean = p.solution[pos.row] == pos.col

    fun withMarks(size: Int, marks: Map<Pos, CellMark>): BoardState {
        var b = BoardState.empty(size)
        for ((p, m) in marks) b = b.withMark(p.row, p.col, m)
        return b
    }

    fun stateOf(board: BoardState): GameState = GameState(board = board)
}
