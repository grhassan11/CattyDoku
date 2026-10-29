package com.threetech.catpuzzle.domain.model

/**
 * Generated puzzle. Flat arrays, row-major.
 * [regions]: region id (0 until size) per cell.
 * [solution]: solution[row] = column of the cat in that row.
 * [techniqueLevel]: hardest logic technique needed to solve (1..5).
 */
class Puzzle(
    val size: Int,
    val regions: IntArray,
    val solution: IntArray,
    val seed: Long,
    val levelIndex: Int,
    val techniqueLevel: Int,
    val generatorVersion: Int,
) {
    init {
        require(regions.size == size * size) { "regions size" }
        require(solution.size == size) { "solution size" }
    }

    fun regionAt(row: Int, col: Int): Int = regions[row * size + col]

    fun solutionCells(): List<Pos> = List(size) { Pos(it, solution[it]) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Puzzle) return false
        return size == other.size &&
            seed == other.seed &&
            levelIndex == other.levelIndex &&
            techniqueLevel == other.techniqueLevel &&
            generatorVersion == other.generatorVersion &&
            regions.contentEquals(other.regions) &&
            solution.contentEquals(other.solution)
    }

    override fun hashCode(): Int {
        var h = size
        h = 31 * h + seed.hashCode()
        h = 31 * h + levelIndex
        h = 31 * h + techniqueLevel
        h = 31 * h + generatorVersion
        h = 31 * h + regions.contentHashCode()
        h = 31 * h + solution.contentHashCode()
        return h
    }

    override fun toString(): String =
        "Puzzle(size=$size, level=$levelIndex, tech=$techniqueLevel, seed=$seed, v=$generatorVersion, " +
            "regions=${regions.joinToString("")}, solution=${solution.joinToString("")})"
}
