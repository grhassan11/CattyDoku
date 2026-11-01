package com.threetech.catpuzzle.domain.engine

/**
 * Bump whenever generator output for a given (spec, seed) changes
 * (RNG use, region growth, repair, solver tagging, curve tables).
 */
const val GENERATOR_VERSION: Int = 4

/** Smallest board v1 generates. */
const val MIN_BOARD_SIZE: Int = 4

/** Engine ceiling (bitmasks assume <= 31, spec says 9). */
const val MAX_BOARD_SIZE: Int = 9
