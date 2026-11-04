package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeAppFlagsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TutorialUseCasesTest {

    @Test
    fun completeSetsTheFlagAndLeavesOtherFlagsAlone() = runTest {
        val start = AppFlags(termsAccepted = true, lastPlayedLevel = 4, installDateEpochDay = 20_000L)
        val repo = FakeAppFlagsRepository(start)
        CompleteTutorial(repo)()
        assertEquals(start.copy(tutorialDone = true), repo.flags.first())
    }

    @Test
    fun completeIsIdempotent() = runTest {
        val repo = FakeAppFlagsRepository()
        val complete = CompleteTutorial(repo)
        complete()
        complete()
        assertEquals(AppFlags(tutorialDone = true), repo.flags.first())
    }
}
