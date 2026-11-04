package com.tm.meow.sudoku.puzzle.domain.model

/** Board coordinate. Flat index = row * size + col. */
data class Pos(val row: Int, val col: Int) {
    fun index(size: Int): Int = row * size + col

    companion object {
        fun fromIndex(index: Int, size: Int): Pos = Pos(index / size, index % size)
    }
}
