package com.threetech.catpuzzle.domain.sudoku.engine

import com.threetech.catpuzzle.domain.engine.SeededRng
import com.threetech.catpuzzle.domain.sudoku.model.SudokuGeometry
import com.threetech.catpuzzle.domain.sudoku.model.SudokuSize
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTechnique

/** Backtracking solver (MRV + bitmasks) and technique grader. Pure, deterministic. */
object SudokuSolver {
    /** Solutions of [grid], stopping at [limit] (default 2: enough to test uniqueness). Invalid givens -> 0. */
    fun countSolutions(size: SudokuSize, grid: IntArray, limit: Int = 2): Int =
        Dfs(SudokuGeometry.of(size), grid, null, limit).also { it.run() }.count

    /** One solution, or null. With [rng] digit order is shuffled (random complete grids from an empty grid). */
    fun solve(size: SudokuSize, grid: IntArray, rng: SeededRng? = null): IntArray? =
        Dfs(SudokuGeometry.of(size), grid, rng, 1).also { it.run() }.sol

    /** Hardest technique the logic solver needs to finish [grid]; GUESS if it gets stuck. */
    fun grade(size: SudokuSize, grid: IntArray): SudokuTechnique = SudokuLogic(SudokuGeometry.of(size), grid).solve()

    private class Dfs(val g: SudokuGeometry, grid: IntArray, val rng: SeededRng?, val limit: Int) {
        val n = g.n
        val v = grid.copyOf()
        val rowM = IntArray(n)
        val colM = IntArray(n)
        val boxM = IntArray(n)
        var count = 0
        var sol: IntArray? = null
        private var valid = true

        init {
            for (c in 0 until g.cells) {
                val d = v[c]
                if (d == 0) continue
                if (d !in 1..n) { valid = false; continue }
                val bit = 1 shl (d - 1)
                val r = c / n
                val cc = c % n
                val b = g.boxOf[c]
                if ((rowM[r] or colM[cc] or boxM[b]) and bit != 0) valid = false
                rowM[r] = rowM[r] or bit
                colM[cc] = colM[cc] or bit
                boxM[b] = boxM[b] or bit
            }
        }

        fun run() {
            if (valid) rec()
        }

        /** Returns true when the search should stop (limit reached). */
        private fun rec(): Boolean {
            var best = -1
            var bestMask = 0
            var bestCnt = 99
            for (c in 0 until g.cells) {
                if (v[c] != 0) continue
                val m = g.full and (rowM[c / n] or colM[c % n] or boxM[g.boxOf[c]]).inv()
                val k = m.countOneBits()
                if (k == 0) return false
                if (k < bestCnt) {
                    best = c; bestMask = m; bestCnt = k
                    if (k == 1) break
                }
            }
            if (best < 0) {
                count++
                if (sol == null) sol = v.copyOf()
                return count >= limit
            }
            val digits = IntArray(bestCnt)
            var i = 0
            for (d in 1..n) if (bestMask and (1 shl (d - 1)) != 0) digits[i++] = d
            rng?.shuffle(digits)
            val r = best / n
            val cc = best % n
            val b = g.boxOf[best]
            for (d in digits) {
                val bit = 1 shl (d - 1)
                v[best] = d; rowM[r] = rowM[r] or bit; colM[cc] = colM[cc] or bit; boxM[b] = boxM[b] or bit
                if (rec()) return true
                v[best] = 0; rowM[r] = rowM[r] xor bit; colM[cc] = colM[cc] xor bit; boxM[b] = boxM[b] xor bit
            }
            return false
        }
    }
}

/** Candidate-bitmask state with human techniques. Also drives [SudokuHintFinder]. */
internal class SudokuLogic(private val g: SudokuGeometry, givens: IntArray) {
    private val n = g.n
    val v = givens.copyOf()
    val cand = IntArray(g.cells)

    init {
        for (c in 0 until g.cells) {
            if (v[c] != 0) continue
            var m = g.full
            for (p in g.peers[c]) if (v[p] != 0) m = m and (1 shl (v[p] - 1)).inv()
            cand[c] = m
        }
    }

    fun place(c: Int, d: Int) {
        v[c] = d
        cand[c] = 0
        val bit = (1 shl (d - 1)).inv()
        for (p in g.peers[c]) cand[p] = cand[p] and bit
    }

    /** Packed (cell shl 4 or digit) or -1. */
    fun nakedSingle(): Int {
        for (c in 0 until g.cells) {
            if (cand[c] != 0 && cand[c].countOneBits() == 1) return (c shl 4) or (cand[c].countTrailingZeroBits() + 1)
        }
        return -1
    }

    /** Packed (cell shl 4 or digit) or -1. */
    fun hiddenSingle(): Int {
        for (u in g.units) for (d in 0 until n) {
            val bit = 1 shl d
            var cnt = 0
            var at = -1
            for (c in u) if (cand[c] and bit != 0) { cnt++; at = c }
            if (cnt == 1) return (at shl 4) or (d + 1)
        }
        return -1
    }

    /** Pointing pairs/triples and box-line reduction (one generic rule). */
    private fun locked(): Boolean {
        var ch = false
        val tmp = IntArray(n)
        for (u in g.units.indices) {
            val k = u / n
            for (d in 0 until n) {
                val bit = 1 shl d
                var cnt = 0
                for (c in g.units[u]) if (cand[c] and bit != 0) tmp[cnt++] = c
                if (cnt < 2) continue
                for (k2 in 0..2) {
                    if (k2 == k) continue
                    val w = g.unitsOf[tmp[0]][k2]
                    var all = true
                    for (i in 1 until cnt) if (g.unitsOf[tmp[i]][k2] != w) { all = false; break }
                    if (!all) continue
                    for (c in g.units[w]) {
                        if (cand[c] and bit != 0 && g.unitsOf[c][k] != u) { cand[c] = cand[c] and bit.inv(); ch = true }
                    }
                }
            }
        }
        return ch
    }

    private fun nakedPair(): Boolean {
        var ch = false
        for (u in g.units) for (i in u.indices) {
            val a = cand[u[i]]
            if (a.countOneBits() != 2) continue
            for (j in i + 1 until u.size) {
                if (cand[u[j]] != a) continue
                for (c in u) {
                    if (c != u[i] && c != u[j] && cand[c] and a != 0) { cand[c] = cand[c] and a.inv(); ch = true }
                }
            }
        }
        return ch
    }

    private fun hiddenPair(): Boolean {
        var ch = false
        val pos = IntArray(n)
        for (u in g.units) {
            for (d in 0 until n) {
                pos[d] = 0
                for (i in u.indices) if (cand[u[i]] and (1 shl d) != 0) pos[d] = pos[d] or (1 shl i)
            }
            for (d1 in 0 until n) {
                if (pos[d1].countOneBits() != 2) continue
                for (d2 in d1 + 1 until n) {
                    if (pos[d2] != pos[d1]) continue
                    val keep = (1 shl d1) or (1 shl d2)
                    for (i in u.indices) {
                        if (pos[d1] and (1 shl i) != 0 && cand[u[i]] and keep.inv() != 0) { cand[u[i]] = cand[u[i]] and keep; ch = true }
                    }
                }
            }
        }
        return ch
    }

    private fun xWing(): Boolean {
        var ch = false
        for (base in 0..1) {
            val cover = 1 - base
            for (d in 0 until n) {
                val bit = 1 shl d
                val ca = IntArray(n) { -1 }
                val cb = IntArray(n) { -1 }
                for (i in 0 until n) {
                    var cnt = 0
                    for (c in g.units[base * n + i]) {
                        if (cand[c] and bit == 0) continue
                        if (cnt == 0) ca[i] = g.unitsOf[c][cover] else cb[i] = g.unitsOf[c][cover]
                        cnt++
                    }
                    if (cnt != 2) ca[i] = -1
                }
                for (i in 0 until n) for (j in i + 1 until n) {
                    if (ca[i] < 0 || ca[i] != ca[j] || cb[i] != cb[j]) continue
                    for (w in intArrayOf(ca[i], cb[i])) for (c in g.units[w]) {
                        val b = g.unitsOf[c][base]
                        if (b != base * n + i && b != base * n + j && cand[c] and bit != 0) { cand[c] = cand[c] and bit.inv(); ch = true }
                    }
                }
            }
        }
        return ch
    }

    /** Cheapest-first until solved or stuck; returns hardest technique used (GUESS if stuck). */
    fun solve(): SudokuTechnique {
        var hardest = SudokuTechnique.NAKED_SINGLE
        var empty = v.count { it == 0 }
        while (empty > 0) {
            val ns = nakedSingle()
            if (ns >= 0) { place(ns shr 4, ns and 15); empty--; continue }
            val hs = hiddenSingle()
            if (hs >= 0) {
                if (hardest < SudokuTechnique.HIDDEN_SINGLE) hardest = SudokuTechnique.HIDDEN_SINGLE
                place(hs shr 4, hs and 15); empty--; continue
            }
            val t = when {
                locked() -> SudokuTechnique.LOCKED_CANDIDATES
                nakedPair() -> SudokuTechnique.NAKED_PAIR
                hiddenPair() -> SudokuTechnique.HIDDEN_PAIR
                xWing() -> SudokuTechnique.X_WING
                else -> return SudokuTechnique.GUESS
            }
            if (t > hardest) hardest = t
        }
        return hardest
    }
}
