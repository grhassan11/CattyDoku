package com.tm.meow.sudoku.puzzle.ui.screens.notification

import app.cash.turbine.test
import com.tm.meow.sudoku.puzzle.domain.usecase.DisableReminders
import com.tm.meow.sudoku.puzzle.domain.usecase.MarkNotificationPromptAsked
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeAppFlagsRepository
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPromptViewModelTest {

    private val settings = FakeSettingsRepository()

    private fun vmTest(block: suspend (FakeAppFlagsRepository, NotificationPromptViewModel) -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val flags = FakeAppFlagsRepository()
            block(
                flags,
                NotificationPromptViewModel(MarkNotificationPromptAsked(flags), DisableReminders(settings)),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun deniedTurnsBothRemindersOff() = vmTest { _, vm ->
        assertTrue(settings.settings.first().streakReminders)
        vm.onAnswered(false)
        val s = settings.settings.first()
        assertFalse(s.streakReminders)
        assertFalse(s.dailyReminders)
        assertFalse(s.sudokuReminders)
    }

    @Test
    fun grantedKeepsReminders() = vmTest { _, vm ->
        vm.onAnswered(true)
        val s = settings.settings.first()
        assertTrue(s.streakReminders)
        assertTrue(s.dailyReminders)
    }

    @Test
    fun answeredRemembersAskedAndFinishes() = vmTest { flags, vm ->
        assertFalse(flags.flags.first().notificationPromptAsked)
        vm.events.test {
            vm.onAnswered()
            assertEquals(NotificationPromptEvent.Finished, awaitItem())
            assertTrue(flags.flags.first().notificationPromptAsked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun repeatedAnswersEmitOnce() = vmTest { _, vm ->
        vm.events.test {
            vm.onAnswered()
            vm.onAnswered()
            assertEquals(NotificationPromptEvent.Finished, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
