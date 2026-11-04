package com.tm.meow.sudoku.puzzle.domain.engine

/**
 * Bump whenever generator output for a given (spec, seed) changes
 * (RNG use, region growth, repair, solver tagging, curve tables).
 * v5: levels 1..10 force one 1-cell region (LevelSpec.singletonRegion).
 */
const val GENERATOR_VERSION: Int = 5

/** Smallest board v1 generates. */
const val MIN_BOARD_SIZE: Int = 4

/** Engine ceiling (bitmasks assume <= 31, spec says 9). */
const val MAX_BOARD_SIZE: Int = 9
