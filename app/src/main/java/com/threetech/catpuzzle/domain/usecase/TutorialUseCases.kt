package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.repository.AppFlagsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Whether the first-run tutorial was finished or skipped (DataStore flag `tutorial_done`). */
class ObserveTutorialDone(private val flags: AppFlagsRepository) {
    operator fun invoke(): Flow<Boolean> = flags.flags.map { it.tutorialDone }.distinctUntilChanged()
}

/** Remember that the tutorial is over (finished or skipped). Idempotent. */
class CompleteTutorial(private val flags: AppFlagsRepository) {
    suspend operator fun invoke() {
        flags.update { it.copy(tutorialDone = true) }
    }
}
