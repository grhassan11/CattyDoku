package com.threetech.catpuzzle.domain.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartFlowTest {

    private fun inputs(
        terms: Boolean = false,
        asked: Boolean = false,
        tutorial: Boolean = false,
        sdk: Int = 34,
    ) = StartInputs(terms, asked, tutorial, sdk)

    @Test
    fun freshInstallApi33PlusRunsEveryStepInOrder() {
        assertEquals(
            listOf(StartStep.WELCOME, StartStep.NOTIFICATION, StartStep.TUTORIAL, StartStep.HOME),
            StartFlow.steps(inputs()),
        )
    }

    @Test
    fun freshInstallBelowApi33SkipsNotification() {
        val expected = listOf(StartStep.WELCOME, StartStep.TUTORIAL, StartStep.HOME)
        assertEquals(expected, StartFlow.steps(inputs(sdk = 32)))
        assertEquals(expected, StartFlow.steps(inputs(sdk = 26)))
    }

    @Test
    fun api33IsTheNotificationBoundary() {
        assertTrue(StartFlow.required(StartStep.NOTIFICATION, inputs(sdk = 33)))
        assertTrue(!StartFlow.required(StartStep.NOTIFICATION, inputs(sdk = 32)))
    }

    @Test
    fun consentIsNotAStartStep() {
        // consent + ads init are gated by the splash itself, before the first step
        assertTrue(StartStep.entries.none { it.name == "CONSENT" })
    }

    @Test
    fun fullMatrixFirstStep() {
        for (terms in listOf(false, true)) for (asked in listOf(false, true)) for (tutorial in listOf(false, true)) {
            for (sdk in listOf(26, 33)) {
                val i = inputs(terms, asked, tutorial, sdk)
                val steps = StartFlow.steps(i)
                assertEquals("$i", StartStep.HOME, steps.last())
                assertEquals("$i", steps.first(), StartFlow.first(i))
                // Terms -> Tutorial order is locked
                if (StartStep.WELCOME in steps && StartStep.TUTORIAL in steps) {
                    assertTrue("$i", steps.indexOf(StartStep.WELCOME) < steps.indexOf(StartStep.TUTORIAL))
                }
            }
        }
    }

    @Test
    fun returningUserGoesStraightToHome() {
        val i = inputs(terms = true, asked = true, tutorial = true)
        assertEquals(listOf(StartStep.HOME), StartFlow.steps(i))
        assertEquals(StartStep.HOME, StartFlow.first(i))
    }

    @Test
    fun notificationAskedOnceNeverAgain() {
        val i = inputs(terms = true, asked = true, tutorial = false)
        assertEquals(StartStep.TUTORIAL, StartFlow.first(i))
    }

    @Test
    fun afterWalksTheFreshSequence() {
        val i = inputs()
        assertEquals(StartStep.WELCOME, StartFlow.first(i))
        assertEquals(StartStep.NOTIFICATION, StartFlow.after(StartStep.WELCOME, i))
        assertEquals(StartStep.TUTORIAL, StartFlow.after(StartStep.NOTIFICATION, i))
        assertEquals(StartStep.HOME, StartFlow.after(StartStep.TUTORIAL, i))
        assertEquals(StartStep.HOME, StartFlow.after(StartStep.HOME, i))
    }

    @Test
    fun afterSkipsStepsThatDoNotApply() {
        assertEquals(StartStep.TUTORIAL, StartFlow.after(StartStep.WELCOME, inputs(sdk = 30)))
        assertEquals(StartStep.HOME, StartFlow.after(StartStep.WELCOME, inputs(tutorial = true, asked = true)))
    }

    @Test
    fun staleSnapshotStillResolvesNextStep() {
        // snapshot predates WELCOME completing (terms = false): after(WELCOME) must not return WELCOME
        assertEquals(StartStep.NOTIFICATION, StartFlow.after(StartStep.WELCOME, inputs(terms = false)))
    }
}
