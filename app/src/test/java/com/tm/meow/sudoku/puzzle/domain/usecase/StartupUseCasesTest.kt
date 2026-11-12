package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import com.tm.meow.sudoku.puzzle.domain.startup.SdkLevelProvider
import com.tm.meow.sudoku.puzzle.domain.startup.StartInputs
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeAppFlagsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupUseCasesTest {

    @Test
    fun readStartInputsReflectsTutorialFlag() = runTest {
        assertEquals(StartInputs(tutorialDone = false), ReadStartInputs(FakeAppFlagsRepository())())
        assertEquals(StartInputs(tutorialDone = true), ReadStartInputs(FakeAppFlagsRepository(AppFlags(tutorialDone = true)))())
    }

    @Test
    fun returningLaunchFollowsTutorialDone() = runTest {
        assertFalse(ObserveReturningLaunch(FakeAppFlagsRepository()).invoke().first())
        assertTrue(ObserveReturningLaunch(FakeAppFlagsRepository(AppFlags(tutorialDone = true))).invoke().first())
    }

    @Test
    fun markNotificationPromptAskedIsIdempotent() = runTest {
        val repo = FakeAppFlagsRepository()
        MarkNotificationPromptAsked(repo)()
        MarkNotificationPromptAsked(repo)()
        assertTrue(repo.flags.first().notificationPromptAsked)
    }

    @Test
    fun shouldAskPureRule() {
        assertTrue(ShouldAskNotificationPermission.shouldAsk(33, false))
        assertTrue(ShouldAskNotificationPermission.shouldAsk(34, false))
        assertFalse(ShouldAskNotificationPermission.shouldAsk(32, false))
        assertFalse(ShouldAskNotificationPermission.shouldAsk(26, false))
        assertFalse(ShouldAskNotificationPermission.shouldAsk(34, true))
    }

    @Test
    fun asksOnlyOnceAndOnlyOnApi33Plus() = runTest {
        val tracker = AdSessionTracker(FakeTimeProvider(today = 20_000L))
        val repo = FakeAppFlagsRepository()
        assertTrue(ShouldAskNotificationPermission(repo, SdkLevelProvider { 34 }, tracker)())
        assertFalse(ShouldAskNotificationPermission(repo, SdkLevelProvider { 30 }, tracker)())
        MarkNotificationPromptAsked(repo)()
        assertFalse(ShouldAskNotificationPermission(repo, SdkLevelProvider { 34 }, tracker)())
    }
}
