package com.threetech.catpuzzle.domain.engine

import kotlin.math.abs

/**
 * Row-by-row bitmask backtracking with forward checking.
 * One cat per row is implied by the rules (N cats, one per row). Counts up to [count]'s cap.
 * Budget is a node count (not wall clock) so results are deterministic.
 */
object SolutionCounter {
    const val DEFAULT_NODE_CAP: Int = 200_000

    enum class Outcome { NONE, UNIQUE, MULTI, ABORTED }

    /** [solutions]: each is solution[row] = col, in discovery order. */
    class CountResult(
        val outcome: Outcome,
        val solutionCount: Int,
        val nodes: Int,
        val solutions: List<IntArray>,
    )

    fun count(
        size: Int,
        regions: IntArray,
        cap: Int = 2,
        nodeCap: Int = DEFAULT_NODE_CAP,
    ): CountResult {
        require(cap >= 2) { "cap must be >= 2" }
        require(size in 1..MAX_BOARD_SIZE) { "size out of range" }
        require(regions.size == size * size) { "regions size" }
        for (g in regions) require(g in 0 until size) { "region id out of range" }

        val s = Search(size, regions, cap, nodeCap)
        s.dfs(0, 0, 0)
        val outcome = when {
            s.sols.size >= 2 -> Outcome.MULTI
            s.aborted -> Outcome.ABORTED
            s.sols.size == 1 -> Outcome.UNIQUE
            else -> Outcome.NONE
        }
        return CountResult(outcome, s.sols.size, s.nodes, s.sols)
    }

    private class Search(
        val n: Int,
        val regions: IntArray,
        val cap: Int,
        val nodeCap: Int,
    ) {
        var nodes = 0
        var aborted = false
        val sols = ArrayList<IntArray>()
        private val cols = IntArray(n)
        private val regionCells: Array<IntArray> = Array(n) { g ->
            val l = ArrayList<Int>()
            for (i in regions.indices) if (regions[i] == g) l.add(i)
            l.toIntArray()
        }

        fun dfs(row: Int, usedCols: Int, usedRegs: Int) {
            if (aborted || sols.size >= cap) return
            if (row == n) {
                sols.add(cols.copyOf())
                return
            }
            for (c in 0 until n) {
                if (((usedCols shr c) and 1) == 1) continue
                if (row > 0 && abs(c - cols[row - 1]) <= 1) continue
                val g = regions[row * n + c]
                if (((usedRegs shr g) and 1) == 1) continue
                nodes++
                if (nodes > nodeCap) {
                    aborted = true
                    return
                }
                cols[row] = c
                val nc = usedCols or (1 shl c)
                val ng = usedRegs or (1 shl g)
                if (!feasible(row, c, nc, ng)) continue
                dfs(row + 1, nc, ng)
                if (aborted || sols.size >= cap) return
            }
        }

        /** Every unused region must still own a cell in a later row, free column, not touching this cat. */
        private fun feasible(row: Int, c: Int, usedCols: Int, usedRegs: Int): Boolean {
            for (g in 0 until n) {
                if (((usedRegs shr g) and 1) == 1) continue
                var ok = false
                for (cell in regionCells[g]) {
                    val rr = cell / n
                    if (rr <= row) continue
                    val cc = cell % n
                    if (((usedCols shr cc) and 1) == 1) continue
                    if (rr == row + 1 && abs(cc - c) <= 1) continue
                    ok = true
                    break
                }
                if (!ok) return false
            }
            return true
        }
    }
}
