package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.Puzzle

/** Hand-built boards + helpers shared by engine tests. */
object Fixtures {
    /**
     * Unique 4x4, solved by naked singles only. Solution cols per row: 1,3,0,2.
     *  2 0 1 1
     *  2 1 1 1
     *  2 2 3 1
     *  2 3 3 3
     */
    val UNIQUE_4 = intArrayOf(2, 0, 1, 1, 2, 1, 1, 1, 2, 2, 3, 1, 2, 3, 3, 3)
    val UNIQUE_4_SOLUTION = intArrayOf(1, 3, 0, 2)

    /** Two solutions on 4x4: cols 1,3,0,2 and 2,0,3,1. */
    val MULTI_4 = intArrayOf(2, 0, 0, 1, 2, 2, 0, 1, 2, 2, 1, 1, 2, 3, 3, 1)

    /** Region 0 sits in row 0 only: first step must be confinement. */
    val CONFINE_4 = intArrayOf(0, 0, 1, 1, 2, 2, 1, 1, 2, 2, 3, 3, 2, 3, 3, 3)

    /** Regions 0 and 1 share rows {0,1} while spanning both: first step must be a k=2 crowding. */
    val CROWD_4 = intArrayOf(0, 0, 1, 1, 0, 2, 2, 1, 2, 2, 3, 3, 2, 3, 3, 3)

    fun board(size: Int, vararg cats: Pos): BoardState {
        var b = BoardState.empty(size)
        for (p in cats) b = b.withMark(p.row, p.col, CellMark.CAT)
        return b
    }

    fun solutionBoard(size: Int, solution: IntArray): BoardState {
        var b = BoardState.empty(size)
        for (r in 0 until size) b = b.withMark(r, solution[r], CellMark.CAT)
        return b
    }

    /** Every region id 0 until size is used and 4-connected. */
    fun regionsContiguous(size: Int, regions: IntArray): Boolean {
        for (g in 0 until size) {
            val cells = regions.indices.filter { regions[it] == g }
            if (cells.isEmpty()) return false
            val seen = HashSet<Int>()
            val stack = ArrayDeque<Int>()
            stack.add(cells[0])
            seen.add(cells[0])
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                val r = c / size
                val col = c % size
                val nbs = ArrayList<Int>()
                if (r > 0) nbs.add(c - size)
                if (r < size - 1) nbs.add(c + size)
                if (col > 0) nbs.add(c - 1)
                if (col < size - 1) nbs.add(c + 1)
                for (nb in nbs) if (regions[nb] == g && seen.add(nb)) stack.add(nb)
            }
            if (seen.size != cells.size) return false
        }
        return true
    }

    /** Full independent check of a generated puzzle. Returns null if fine, else a message. */
    fun checkPuzzle(p: Puzzle): String? {
        val n = p.size
        for (g in p.regions) if (g !in 0 until n) return "region id out of range"
        if (!regionsContiguous(n, p.regions)) return "regions not contiguous/used"
        val solRegions = HashSet<Int>()
        for (r in 0 until n) solRegions.add(p.regionAt(r, p.solution[r]))
        if (solRegions.size != n) return "solution not one cat per region"
        val an = BoardValidator.analyse(p, solutionBoard(n, p.solution))
        if (!an.solved) return "solution board not solved by validator"
        val cr = SolutionCounter.count(n, p.regions)
        if (cr.outcome != SolutionCounter.Outcome.UNIQUE) return "not unique: ${cr.outcome}"
        if (!cr.solutions[0].contentEquals(p.solution)) return "unique solution differs from stored"
        val lr = LogicSolver.solve(n, p.regions)
        if (!lr.solved) return "not logic-solvable"
        if (lr.maxLevel != p.techniqueLevel) return "techniqueLevel ${p.techniqueLevel} != solver ${lr.maxLevel}"
        if (p.generatorVersion != GENERATOR_VERSION) return "generator version"
        return null
    }

    /** Board identity independent of region ids: relabel by first appearance + solution. */
    fun canonicalKey(p: Puzzle): String {
        val map = HashMap<Int, Int>()
        val sb = StringBuilder()
        for (g in p.regions) sb.append(map.getOrPut(g) { map.size })
        sb.append('|').append(p.solution.joinToString(""))
        return sb.toString()
    }
}
