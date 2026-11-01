package com.threetech.catpuzzle.domain.sudoku.model

/** Box layout inside a grid: [rows] x [cols] cells per box. */
data class BoxShape(val rows: Int, val cols: Int)

/** Grid size. Cell index = r * n + c; digits 1..n; 0 = empty. n == box.rows * box.cols. */
enum class SudokuSize(val n: Int, val box: BoxShape) {
    S4(4, BoxShape(2, 2)),
    S6(6, BoxShape(2, 3)),
    S9(9, BoxShape(3, 3));

    val cells: Int get() = n * n
}

/**
 * Logic techniques, cheapest first. Order is the difficulty order (compareTo is meaningful).
 * GUESS = solver stuck (needs trial and error); also used by hints for "revealed from solution".
 */
enum class SudokuTechnique { NAKED_SINGLE, HIDDEN_SINGLE, LOCKED_CANDIDATES, NAKED_PAIR, HIDDEN_PAIR, X_WING, GUESS }

/**
 * Difficulty tiers. Each tier = size + clue range + technique band (hardest technique the logic solver
 * needs must fall in [minTechnique, maxTechnique]). Changing any value changes generated puzzles =>
 * bump SUDOKU_GENERATOR_VERSION.
 */
enum class SudokuTier(
    val size: SudokuSize,
    val minClues: Int,
    val maxClues: Int,
    val minTechnique: SudokuTechnique,
    val maxTechnique: SudokuTechnique,
    val scoreMultiplier: Int,
    val parSeconds: Int,
    val unlockWins: Int,
) {
    EASY(SudokuSize.S4, 8, 10, SudokuTechnique.NAKED_SINGLE, SudokuTechnique.HIDDEN_SINGLE, 1, 120, 0),
    MEDIUM(SudokuSize.S6, 14, 17, SudokuTechnique.HIDDEN_SINGLE, SudokuTechnique.NAKED_PAIR, 2, 300, 0),
    HARD(SudokuSize.S9, 30, 36, SudokuTechnique.HIDDEN_SINGLE, SudokuTechnique.HIDDEN_SINGLE, 3, 600, 2),
    EXPERT(SudokuSize.S9, 27, 32, SudokuTechnique.LOCKED_CANDIDATES, SudokuTechnique.NAKED_PAIR, 4, 900, 4),
    MASTER(SudokuSize.S9, 24, 30, SudokuTechnique.NAKED_PAIR, SudokuTechnique.HIDDEN_PAIR, 5, 1200, 10),
    EXTREME(SudokuSize.S9, 22, 27, SudokuTechnique.HIDDEN_PAIR, SudokuTechnique.X_WING, 6, 1500, 14);

    /** Tier whose wins unlock this one (null = always open). Needs [unlockWins] wins in it. */
    val unlockTier: SudokuTier?
        get() = when (this) {
            EASY, MEDIUM -> null
            HARD -> MEDIUM
            EXPERT -> HARD
            MASTER -> EXPERT
            EXTREME -> MASTER
        }

    fun isUnlocked(winsByTier: Map<SudokuTier, Int>): Boolean {
        val req = unlockTier ?: return true
        return (winsByTier[req] ?: 0) >= unlockWins
    }
}

/** [matchedTier] false = generator hit its attempt cap and returned the closest valid unique puzzle. */
class SudokuPuzzle(
    val tier: SudokuTier,
    val givens: IntArray,
    val solution: IntArray,
    val seed: Long,
    val generatorVersion: Int,
    val rating: SudokuTechnique,
    val matchedTier: Boolean,
) {
    val size: SudokuSize get() = tier.size
    val clues: Int get() = givens.count { it != 0 }
}

/** A hint: put [digit] in [cell]. [technique] GUESS = revealed straight from the solution. */
data class SudokuHint(val cell: Int, val digit: Int, val technique: SudokuTechnique)

/** Precomputed units/peers per size. Units: rows 0..n-1, cols n..2n-1, boxes 2n..3n-1. */
class SudokuGeometry private constructor(val size: SudokuSize) {
    val n = size.n
    val cells = size.cells
    val full = (1 shl n) - 1
    val boxOf = IntArray(cells) { (it / n / size.box.rows) * (n / size.box.cols) + (it % n / size.box.cols) }
    val units: Array<IntArray> = Array(3 * n) { u ->
        val k = u / n
        val i = u % n
        (0 until cells).filter { c ->
            when (k) {
                0 -> c / n == i
                1 -> c % n == i
                else -> boxOf[c] == i
            }
        }.toIntArray()
    }

    /** Per cell: absolute unit index of [row, col, box]. */
    val unitsOf: Array<IntArray> = Array(cells) { intArrayOf(it / n, n + it % n, 2 * n + boxOf[it]) }
    val peers: Array<IntArray> = Array(cells) { c ->
        val s = LinkedHashSet<Int>()
        for (u in unitsOf[c]) for (p in units[u]) if (p != c) s.add(p)
        s.toIntArray()
    }

    companion object {
        private val cache = SudokuSize.values().map { SudokuGeometry(it) }
        fun of(size: SudokuSize): SudokuGeometry = cache[size.ordinal]
    }
}
