package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.HintRule
import com.threetech.catpuzzle.domain.model.Puzzle

/**
 * Cells a technique removes, with the candidate cells that justify it. Flat indices.
 * [rule] / [focus]: rule behind the step and the full unit cells involved (hint overlay spotlight only; never read by solving logic).
 */
internal class Elimination(
    val targets: List<Int>,
    val reasons: List<Int>,
    val rule: HintRule? = null,
    val focus: List<Int> = emptyList(),
)

/** Naked single with the unit that forced it: [family] 0 region, 1 row, 2 column. */
internal class NakedSingle(val cell: Int, val family: Int, val line: Int)

/**
 * Candidate state derived from the player's CORRECT cats only (wrong cats and all X marks ignored).
 * Same rules as [LogicSolver]; unlike it, steps are returned as data instead of applied silently.
 * Families: 0 region, 1 row, 2 column.
 * [cand]: cell can still hold a cat (placed cells are not candidates, their lines are [done]).
 */
internal class CandidateGrid private constructor(
    private val geo: Geo,
    private val cand: BooleanArray,
    private val placed: BooleanArray,
    private val done: Array<BooleanArray>,
    var catCount: Int,
) {
    fun copy(): CandidateGrid = CandidateGrid(
        geo, cand.copyOf(), placed.copyOf(), Array(3) { done[it].copyOf() }, catCount,
    )

    fun place(cell: Int) {
        placed[cell] = true
        cand[cell] = false
        catCount++
        for (f in 0..2) {
            val line = geo.lineOf[f][cell]
            done[f][line] = true
            for (x in geo.lineCells[f][line]) if (!placed[x]) cand[x] = false
        }
        for (x in geo.neighbors[cell]) if (!placed[x]) cand[x] = false
    }

    fun eliminate(cells: List<Int>) {
        for (c in cells) cand[c] = false
    }

    /** First unit (family, line order) with exactly one candidate: that cell. */
    fun nakedSingle(): Int? = nakedSingleDetail()?.cell

    /** Same pick as [nakedSingle], plus the unit that has the single candidate. */
    fun nakedSingleDetail(): NakedSingle? {
        for (f in 0..2) {
            for (l in 0 until geo.n) {
                if (done[f][l]) continue
                var count = 0
                var last = -1
                for (x in geo.lineCells[f][l]) {
                    if (cand[x]) {
                        count++
                        last = x
                        if (count > 1) break
                    }
                }
                if (count == 1) return NakedSingle(last, f, l)
            }
        }
        return null
    }

    /** Hall-style elimination of size [k] between ordered family pairs; first hit wins. */
    fun hall(k: Int): Elimination? {
        val n = geo.n
        for (a in 0..2) {
            var active = 0
            for (l in 0 until n) if (!done[a][l]) active = active or (1 shl l)
            for (c in 0..2) {
                if (a == c) continue
                val touch = IntArray(n)
                for (cell in 0 until geo.cells) {
                    if (cand[cell]) {
                        val la = geo.lineOf[a][cell]
                        touch[la] = touch[la] or (1 shl geo.lineOf[c][cell])
                    }
                }
                for (mask in 1 until (1 shl n)) {
                    if (mask.countOneBits() != k) continue
                    if ((mask and active.inv()) != 0) continue
                    var union = 0
                    for (l in 0 until n) if (((mask shr l) and 1) == 1) union = union or touch[l]
                    if (union.countOneBits() != k) continue
                    val targets = ArrayList<Int>()
                    val reasons = ArrayList<Int>()
                    for (cell in 0 until geo.cells) {
                        if (!cand[cell]) continue
                        val inMask = ((mask shr geo.lineOf[a][cell]) and 1) == 1
                        if (inMask) {
                            reasons.add(cell)
                        } else if (((union shr geo.lineOf[c][cell]) and 1) == 1) {
                            targets.add(cell)
                        }
                    }
                    if (targets.isNotEmpty()) {
                        val focus = LinkedHashSet<Int>()
                        for (l in 0 until n) if (((mask shr l) and 1) == 1) geo.lineCells[a][l].forEach { focus.add(it) }
                        for (l in 0 until n) if (((union shr l) and 1) == 1) geo.lineCells[c][l].forEach { focus.add(it) }
                        return Elimination(targets, reasons, HintRule.fromFamily(a), focus.toList())
                    }
                }
            }
        }
        return null
    }

    /** First candidate whose trial placement empties some unit. Reasons: that unit's candidates. */
    fun lookahead(): Elimination? {
        for (cell in 0 until geo.cells) {
            if (!cand[cell]) continue
            val trial = copy()
            trial.place(cell)
            val dead = trial.firstEmptyUnit() ?: continue
            val reasons = geo.lineCells[dead.first][dead.second].filter { cand[it] }
            // Adjacency is the reason when some killed candidate of the dead unit shares no row / column / region with the trial cell.
            val touchKill = geo.lineCells[dead.first][dead.second].any { x ->
                cand[x] && x != cell && x in geo.neighbors[cell] &&
                    (0..2).none { f -> geo.lineOf[f][x] == geo.lineOf[f][cell] }
            }
            val focus = LinkedHashSet<Int>()
            geo.lineCells[dead.first][dead.second].forEach { focus.add(it) }
            focus.add(cell)
            if (touchKill) geo.neighbors[cell].forEach { focus.add(it) }
            return Elimination(
                listOf(cell),
                reasons,
                if (touchKill) HintRule.TOUCH else HintRule.fromFamily(dead.first),
                focus.toList(),
            )
        }
        return null
    }

    /** (family, line) of the first unfinished unit with no candidate, or null. */
    private fun firstEmptyUnit(): Pair<Int, Int>? {
        for (f in 0..2) {
            for (l in 0 until geo.n) {
                if (done[f][l]) continue
                var any = false
                for (x in geo.lineCells[f][l]) {
                    if (cand[x]) {
                        any = true
                        break
                    }
                }
                if (!any) return f to l
            }
        }
        return null
    }

    /**
     * Unfinished unit with the fewest candidates (ties: family 0..2, then line index).
     * Returns (family, line) or null when every unit is done.
     */
    fun mostForcedUnit(): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestCount = Int.MAX_VALUE
        for (f in 0..2) {
            for (l in 0 until geo.n) {
                if (done[f][l]) continue
                var count = 0
                for (x in geo.lineCells[f][l]) if (cand[x]) count++
                if (count < bestCount) {
                    bestCount = count
                    best = f to l
                }
            }
        }
        return best
    }

    fun unitCells(family: Int, line: Int): IntArray = geo.lineCells[family][line]

    private class Geo(val n: Int, regions: IntArray) {
        val cells: Int = n * n
        val lineOf: Array<IntArray> = arrayOf(
            regions.copyOf(),
            IntArray(cells) { it / n },
            IntArray(cells) { it % n },
        )
        val lineCells: Array<Array<IntArray>> = Array(3) { f ->
            Array(n) { l -> (0 until cells).filter { lineOf[f][it] == l }.toIntArray() }
        }
        val neighbors: Array<IntArray> = Array(cells) { cell ->
            val r = cell / n
            val c = cell % n
            val out = ArrayList<Int>()
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val rr = r + dr
                val cc = c + dc
                if (rr in 0 until n && cc in 0 until n) out.add(rr * n + cc)
            }
            out.toIntArray()
        }
    }

    companion object {
        /** Grid after placing only the board's correct cats. */
        fun fromCorrectCats(puzzle: Puzzle, board: BoardState): CandidateGrid {
            val n = puzzle.size
            val geo = Geo(n, puzzle.regions)
            val grid = CandidateGrid(
                geo,
                BooleanArray(geo.cells) { true },
                BooleanArray(geo.cells),
                Array(3) { BooleanArray(n) },
                0,
            )
            for (row in 0 until n) {
                val idx = row * n + puzzle.solution[row]
                if (board.marks[idx] == CellMark.CAT) grid.place(idx)
            }
            return grid
        }
    }
}
