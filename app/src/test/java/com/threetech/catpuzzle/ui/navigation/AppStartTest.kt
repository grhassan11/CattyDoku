package com.threetech.catpuzzle.ui.navigation

import com.threetech.catpuzzle.domain.model.AppFlags
import com.threetech.catpuzzle.domain.startup.SdkLevelProvider
import com.threetech.catpuzzle.domain.startup.StartFlow
import com.threetech.catpuzzle.domain.startup.StartInputs
import com.threetech.catpuzzle.domain.startup.StartStep
import com.threetech.catpuzzle.domain.usecase.ReadStartInputs
import com.threetech.catpuzzle.ui.screens.game.FakeAppFlagsRepository
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
}
