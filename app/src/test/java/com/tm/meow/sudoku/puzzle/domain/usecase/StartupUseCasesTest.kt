package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import com.tm.meow.sudoku.puzzle.domain.startup.SdkLevelProvider
import com.tm.meow.sudoku.puzzle.domain.startup.StartFlow
import com.tm.meow.sudoku.puzzle.domain.startup.StartInputs
import com.tm.meow.sudoku.puzzle.domain.startup.StartStep
import com.tm.meow.sudoku.puzzle.domain.startup.TermsPolicy
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeAppFlagsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupUseCasesTest {

    @Test
    fun acceptTermsStoresFlagAndCurrentVersion() = runTest {
        val repo = FakeAppFlagsRepository()
        assertFalse(ObserveTermsAccepted(repo).invoke().first())
        AcceptTerms(repo)()
        val f = repo.flags.first()
        assertTrue(f.termsAccepted)
        assertEquals(TermsPolicy.CURRENT_VERSION, f.termsVersion)
        assertTrue(ObserveTermsAccepted(repo).invoke().first())
    }

    @Test
    fun acceptTermsIsIdempotentAndKeepsOtherFlags() = runTest {
        val repo = FakeAppFlagsRepository(AppFlags(tutorialDone = true, lastPlayedLevel = 4))
        AcceptTerms(repo)()
        AcceptTerms(repo)()
        assertEquals(
            AppFlags(termsAccepted = true, termsVersion = TermsPolicy.CURRENT_VERSION, tutorialDone = true, lastPlayedLevel = 4),
            repo.flags.first(),
        )
    }

    @Test
    fun termsVersionBumpRepromptsThenReaccepts() = runTest {
        val repo = FakeAppFlagsRepository()
        AcceptTerms(repo, version = 1)()
        // app update raises the required version: acceptance of v1 no longer counts
        assertFalse(TermsPolicy.isAccepted(repo.flags.first(), currentVersion = 2))
        AcceptTerms(repo, version = 2)()
        assertTrue(TermsPolicy.isAccepted(repo.flags.first(), currentVersion = 2))
    }

    @Test
    fun oldAcceptedFlagWithoutVersionReadsNotAccepted() = runTest {
        val repo = FakeAppFlagsRepository(AppFlags(termsAccepted = true))
        assertFalse(ObserveTermsAccepted(repo).invoke().first())
    }

    @Test
    fun notificationPromptAskedOnce() = runTest {
        val repo = FakeAppFlagsRepository(AppFlags(termsAccepted = true, termsVersion = 1))
        val sdk = SdkLevelProvider { 34 }
        assertEquals(StartStep.NOTIFICATION, StartFlow.first(ReadStartInputs(repo, sdk)()))
        assertEquals(StartStep.TUTORIAL, StartFlow.after(StartStep.NOTIFICATION, ReadStartInputs(repo, sdk)()))

        MarkNotificationPromptAsked(repo)()
        MarkNotificationPromptAsked(repo)()

        val after = ReadStartInputs(repo, sdk)()
        assertTrue(after.notificationPromptAsked)
        assertEquals(StartStep.TUTORIAL, StartFlow.first(after))
    }

    @Test
    fun readStartInputsCombinesFlagsAndSdk() = runTest {
        val repo = FakeAppFlagsRepository(AppFlags(termsAccepted = true, termsVersion = 1, tutorialDone = true))
        assertEquals(
            StartInputs(termsAccepted = true, notificationPromptAsked = false, tutorialDone = true, sdkInt = 30),
            ReadStartInputs(repo, SdkLevelProvider { 30 })(),
        )
    }

    @Test
    fun readStartInputsTreatsStaleTermsAsNotAccepted() = runTest {
        val repo = FakeAppFlagsRepository(AppFlags(termsAccepted = true, termsVersion = 0))
        assertFalse(ReadStartInputs(repo, SdkLevelProvider { 34 })().termsAccepted)
    }
}
