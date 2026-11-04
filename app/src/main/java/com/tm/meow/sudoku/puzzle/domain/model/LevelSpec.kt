package com.tm.meow.sudoku.puzzle.domain.model

/**
 * Generation request for one level.
 * [targetTechnique]: hardest logic technique wanted (see Technique constants, 1..5).
 * [balancePercent]: 0..100 chance each growth step favours the smallest region.
 * [minOpeningSingles]: minimum naked-single placements available before a harder technique is needed.
 * [singletonRegion]: force exactly one 1-cell region (beginner levels). If that is impossible within
 * budget the generator falls back to the same spec without it.
 */
data class LevelSpec(
    val levelIndex: Int,
    val size: Int,
    val targetTechnique: Int,
    val minRegionSize: Int,
    val balancePercent: Int,
    val minOpeningSingles: Int,
    val singletonRegion: Boolean = false,
)
