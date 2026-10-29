package com.threetech.catpuzzle.ui.screens.notification

import app.cash.turbine.test
import com.threetech.catpuzzle.domain.usecase.MarkNotificationPromptAsked
import com.threetech.catpuzzle.ui.screens.game.FakeAppFlagsRepository
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

    private fun vmTest(block: suspend (FakeAppFlagsRepository, NotificationPromptViewModel) -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val flags = FakeAppFlagsRepository()
            block(flags, NotificationPromptViewModel(MarkNotificationPromptAsked(flags)))
        } finally {
            Dispatchers.resetMain()
        }
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
