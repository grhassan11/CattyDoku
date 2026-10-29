package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.model.AppFlags
import com.threetech.catpuzzle.ui.screens.game.FakeAppFlagsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialUseCasesTest {

    @Test
    fun tutorialNotDoneByDefault() = runTest {
        assertFalse(ObserveTutorialDone(FakeAppFlagsRepository())().first())
    }

    @Test
    fun completeSetsTheFlagAndLeavesOtherFlagsAlone() = runTest {
        val start = AppFlags(termsAccepted = true, lastPlayedLevel = 4, installDateEpochDay = 20_000L)
        val repo = FakeAppFlagsRepository(start)
        CompleteTutorial(repo)()
        assertTrue(ObserveTutorialDone(repo)().first())
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
