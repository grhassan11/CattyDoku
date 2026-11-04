package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository

/** Remember that the tutorial is over (finished or skipped). Idempotent. */
class CompleteTutorial(private val flags: AppFlagsRepository) {
    suspend operator fun invoke() {
        flags.update { it.copy(tutorialDone = true) }
    }
}
