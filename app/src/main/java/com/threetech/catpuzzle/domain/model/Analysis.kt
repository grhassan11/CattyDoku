package com.threetech.catpuzzle.domain.model

/**
 * Result of validating a board.
 * [conflictCells]: flat indices of cat cells that clash (same row/col/region or touching).
 * done flags: unit has exactly one cat and that cat is conflict-free.
 */
data class Analysis(
    val size: Int,
    val conflictCells: Set<Int>,
    val regionDone: List<Boolean>,
    val rowDone: List<Boolean>,
    val colDone: List<Boolean>,
    val solved: Boolean,
) {
    val hasConflicts: Boolean get() = conflictCells.isNotEmpty()
}
