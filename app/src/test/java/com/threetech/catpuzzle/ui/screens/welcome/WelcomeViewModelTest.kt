package com.threetech.catpuzzle.ui.screens.welcome

import app.cash.turbine.test
import com.threetech.catpuzzle.domain.startup.TermsPolicy
import com.threetech.catpuzzle.domain.usecase.AcceptTerms
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
class WelcomeViewModelTest {

    private fun vmTest(block: suspend (FakeAppFlagsRepository, WelcomeViewModel) -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val flags = FakeAppFlagsRepository()
            block(flags, WelcomeViewModel(AcceptTerms(flags)))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun nothingSavedUntilAccept() = vmTest { flags, _ ->
        assertFalse(flags.flags.first().termsAccepted)
    }

    @Test
    fun acceptSavesFlagAndVersionThenEmitsAccepted() = vmTest { flags, vm ->
        vm.events.test {
            vm.onAccept()
            assertEquals(WelcomeEvent.Accepted, awaitItem())
            val f = flags.flags.first()
            assertTrue(f.termsAccepted)
            assertEquals(TermsPolicy.CURRENT_VERSION, f.termsVersion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun doubleTapEmitsOnce() = vmTest { _, vm ->
        vm.events.test {
            vm.onAccept()
            vm.onAccept()
            assertEquals(WelcomeEvent.Accepted, awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
