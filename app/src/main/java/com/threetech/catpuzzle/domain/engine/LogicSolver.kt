package com.threetech.catpuzzle.domain.engine

/** Technique levels reported by [LogicSolver]. Higher = harder. */
object Technique {
    const val NAKED_SINGLE = 1
    const val CONFINEMENT = 2
    const val CROWDING_PAIR = 3
    const val CROWDING_HALL = 4
    const val LOOKAHEAD = 5
    const val MAX = 5
}

/**
 * [maxLevel]: hardest technique used (0 if none applied).
 * [openingSingles]: naked-single placements made before any harder technique was needed.
 * [levelCounts]: steps per level, index = level.
 * [cats]: cats[row] = col, -1 where not placed.
 */
class LogicResult(
    val solved: Boolean,
    val contradiction: Boolean,
    val maxLevel: Int,
    val openingSingles: Int,
    val steps: Int,
    val levelCounts: IntArray,
    val cats: IntArray,
) {
    val stuck: Boolean get() = !solved && !contradiction
}

/**
 * Human-style solver. Lowest applicable technique first, restart after each step.
 *
 * 1 naked single: a region / row / column has exactly one candidate cell.
 * 2 confinement (Hall k=1): a unit's candidates sit in one line of another family
 *   (e.g. region inside one row) => clear that line elsewhere.
 * 3 crowding k=2, 4 crowding k=3..4: k units of one family whose candidates fit in exactly
 *   k lines of another family => those lines are used up, clear them for all other units.
 * 5 look-ahead: trial cat placement + basic propagation empties some unit => cell is out.
 *
 * Families: 0 region, 1 row, 2 column.
 */
object LogicSolver {

    /**
     * [preplaced]: optional flat cell index of a cat that is already on the board (starter cat).
     * It is placed before the first step and is NOT counted as a step or technique use. Default -1
     * (none) keeps the generator / rating path byte-identical.
     */
    fun solve(size: Int, regions: IntArray, preplaced: Int = -1): LogicResult {
        require(size in 1..MAX_BOARD_SIZE) { "size out of range" }
        require(regions.size == size * size) { "regions size" }
        require(preplaced == -1 || preplaced in 0 until size * size) { "preplaced out of range" }

        val b = Board(Geo(size, regions))
        if (preplaced >= 0) b.place(preplaced)
        val counts = IntArray(Technique.MAX + 1)
        var maxLevel = 0
        var steps = 0
        var opening = 0
        var openingOpen = true
        var contradiction = false

        while (b.catCount < size) {
            if (b.hasContradiction()) {
                contradiction = true
                break
            }
            val level = step(b)
            if (level == 0) break
            steps++
            counts[level]++
            if (level > maxLevel) maxLevel = level
            if (level == Technique.NAKED_SINGLE) {
                if (openingOpen) opening++
            } else {
                openingOpen = false
            }
        }

        val cats = IntArray(size) { -1 }
        for (cell in 0 until size * size) if (b.placed[cell]) cats[cell / size] = cell % size
        return LogicResult(
            solved = !contradiction && b.catCount == size,
            contradiction = contradiction,
            maxLevel = maxLevel,
            openingSingles = opening,
            steps = steps,
            levelCounts = counts,
            cats = cats,
        )
    }

    private fun step(b: Board): Int {
        if (nakedSingle(b)) return Technique.NAKED_SINGLE
        if (hall(b, 1)) return Technique.CONFINEMENT
        if (hall(b, 2)) return Technique.CROWDING_PAIR
        if (hall(b, 3) || hall(b, 4)) return Technique.CROWDING_HALL
        if (lookahead(b)) return Technique.LOOKAHEAD
        return 0
    }

    private fun nakedSingle(b: Board): Boolean {
        val g = b.geo
        for (f in 0..2) {
            for (l in 0 until g.n) {
                if (b.done[f][l]) continue
                var count = 0
                var last = -1
                for (x in g.lineCells[f][l]) {
                    if (b.cand[x]) {
                        count++
                        last = x
                        if (count > 1) break
                    }
                }
                if (count == 1) {
                    b.place(last)
                    return true
                }
            }
        }
        return false
    }

    /** Hall-style elimination of size k between every ordered pair of distinct families. */
    private fun hall(b: Board, k: Int): Boolean {
        val g = b.geo
        val n = g.n
        for (a in 0..2) {
            var active = 0
            for (l in 0 until n) if (!b.done[a][l]) active = active or (1 shl l)
            for (c in 0..2) {
                if (a == c) continue
                // touch[l] = bitmask of c-lines holding candidates of a-line l
                val touch = IntArray(n)
                for (cell in 0 until g.cells) {
                    if (b.cand[cell] && !b.placed[cell]) {
                        val la = g.lineOf[a][cell]
                        touch[la] = touch[la] or (1 shl g.lineOf[c][cell])
                    }
                }
                for (mask in 1 until (1 shl n)) {
                    if (mask.countOneBits() != k) continue
                    if ((mask and active.inv()) != 0) continue
                    var union = 0
                    for (l in 0 until n) if (((mask shr l) and 1) == 1) union = union or touch[l]
                    if (union.countOneBits() != k) continue
                    var changed = false
                    for (cell in 0 until g.cells) {
                        if (!b.cand[cell] || b.placed[cell]) continue
                        if (((union shr g.lineOf[c][cell]) and 1) == 0) continue
                        if (((mask shr g.lineOf[a][cell]) and 1) == 1) continue
                        b.cand[cell] = false
                        changed = true
                    }
                    if (changed) return true
                }
            }
        }
        return false
    }

    private fun lookahead(b: Board): Boolean {
        for (cell in 0 until b.geo.cells) {
            if (!b.cand[cell] || b.placed[cell]) continue
            val trial = b.copy()
            trial.place(cell)
            if (trial.hasContradiction()) {
                b.cand[cell] = false
                return true
            }
        }
        return false
    }

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

    private class Board(
        val geo: Geo,
        val cand: BooleanArray,
        val placed: BooleanArray,
        val done: Array<BooleanArray>,
        var catCount: Int,
    ) {
        constructor(geo: Geo) : this(
            geo,
            BooleanArray(geo.cells) { true },
            BooleanArray(geo.cells),
            Array(3) { BooleanArray(geo.n) },
            0,
        )

        fun copy(): Board = Board(
            geo,
            cand.copyOf(),
            placed.copyOf(),
            Array(3) { done[it].copyOf() },
            catCount,
        )

        /** Put a cat: mark lines done, clear the rest of its lines and the 8 neighbours. */
        fun place(cell: Int) {
            placed[cell] = true
            catCount++
            for (f in 0..2) {
                val line = geo.lineOf[f][cell]
                done[f][line] = true
                for (x in geo.lineCells[f][line]) if (x != cell && !placed[x]) cand[x] = false
            }
            for (x in geo.neighbors[cell]) if (!placed[x]) cand[x] = false
        }

        /** Some unfinished unit has no candidate left. */
        fun hasContradiction(): Boolean {
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
                    if (!any) return true
                }
            }
            return false
        }
    }
}
