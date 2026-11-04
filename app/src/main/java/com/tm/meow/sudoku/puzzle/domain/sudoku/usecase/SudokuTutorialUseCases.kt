package com.tm.meow.sudoku.puzzle.domain.sudoku.usecase

import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuSize
import kotlinx.coroutines.flow.first

/**
 * One-shot read of the Sudoku tutorial flag of one board size. True = finished or skipped. The 9x9 flag is the original
 * DataStore key `sudoku_tutorial_done`; the other sizes have their own keys (see PrefKeys).
 */
class IsSudokuTutorialDone(private val flags: AppFlagsRepository) {
    suspend operator fun invoke(size: SudokuSize): Boolean = size in flags.flags.first().sudokuTutorialSeen
}

/** Remember that the tutorial of [size] is over (finished or skipped). Idempotent. Never touches progress, hints or streak. */
class CompleteSudokuTutorial(private val flags: AppFlagsRepository) {
    suspend operator fun invoke(size: SudokuSize) {
        flags.update { it.copy(sudokuTutorialSeen = it.sudokuTutorialSeen + size) }
    }
}
