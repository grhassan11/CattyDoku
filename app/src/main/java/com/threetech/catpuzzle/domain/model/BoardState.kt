package com.threetech.catpuzzle.domain.model

/** Immutable player board: flat row-major marks. */
data class BoardState(val size: Int, val marks: List<CellMark>) {
    init {
        require(marks.size == size * size) { "marks size ${marks.size} != ${size * size}" }
    }

    operator fun get(row: Int, col: Int): CellMark = marks[row * size + col]

    fun withMark(row: Int, col: Int, mark: CellMark): BoardState {
        val copy = marks.toMutableList()
        copy[row * size + col] = mark
        return BoardState(size, copy)
    }

    val catCount: Int get() = marks.count { it == CellMark.CAT }

    companion object {
        fun empty(size: Int): BoardState = BoardState(size, List(size * size) { CellMark.EMPTY })
    }
}
