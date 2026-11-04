package com.tm.meow.sudoku.puzzle.ui.navigation

import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import com.tm.meow.sudoku.puzzle.domain.startup.SdkLevelProvider
import com.tm.meow.sudoku.puzzle.domain.startup.StartFlow
import com.tm.meow.sudoku.puzzle.domain.startup.StartInputs
import com.tm.meow.sudoku.puzzle.domain.startup.StartStep
import com.tm.meow.sudoku.puzzle.domain.usecase.ReadStartInputs
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeAppFlagsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartTest {

    @Test
    fun everyStepHasItsOwnRoute() {
        assertEquals(Routes.WELCOME, routeFor(StartStep.WELCOME))
        assertEquals(Routes.NOTIFICATION, routeFor(StartStep.NOTIFICATION))
        assertEquals(Routes.TUTORIAL, routeFor(StartStep.TUTORIAL))
        assertEquals(Routes.HOME, routeFor(StartStep.HOME))
        assertEquals(StartStep.entries.size, StartStep.entries.map(::routeFor).toSet().size)
    }

    @Test
    fun routesAreDistinctFromSplashAndGame() {
        val routes = listOf(Routes.SPLASH, Routes.WELCOME, Routes.NOTIFICATION, Routes.TUTORIAL, Routes.HOME, Routes.GAME)
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun viewModelPublishesInputsFromFlagsAndSdk() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val first = AppStartViewModel(ReadStartInputs(FakeAppFlagsRepository(AppFlags()), SdkLevelProvider { 34 }))
            assertEquals(
                StartInputs(termsAccepted = false, notificationPromptAsked = false, tutorialDone = false, sdkInt = 34),
                first.startInputs.value,
            )
            assertEquals(StartStep.WELCOME, StartFlow.first(first.startInputs.value!!))

            val later = AppStartViewModel(
                ReadStartInputs(
                    FakeAppFlagsRepository(
                        AppFlags(termsAccepted = true, termsVersion = 1, notificationPromptAsked = true, tutorialDone = true),
                    ),
                    SdkLevelProvider { 34 },
                ),
            )
            assertNotNull(later.startInputs.value)
            assertEquals(StartStep.HOME, StartFlow.first(later.startInputs.value!!))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun startInputsFallBackToFirstLaunchDefaultsWhenFlagsReadFails() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repo = object : com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository {
                override val flags: kotlinx.coroutines.flow.Flow<AppFlags> =
                    kotlinx.coroutines.flow.flow { throw java.io.IOException("corrupt prefs") }
                override suspend fun update(transform: (AppFlags) -> AppFlags) = Unit
                override suspend fun setInstallDateIfAbsent(epochDay: Long) = Unit
            }
            val vm = AppStartViewModel(ReadStartInputs(repo, SdkLevelProvider { 34 }))
            assertEquals(
                StartInputs(termsAccepted = false, notificationPromptAsked = false, tutorialDone = false, sdkInt = 34),
                vm.startInputs.value,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }
}
