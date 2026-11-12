package com.tm.meow.sudoku.puzzle.domain.startup

import org.junit.Assert.assertEquals
import org.junit.Test

class StartFlowTest {

    private fun inputs(tutorial: Boolean = false) = StartInputs(tutorial)

    @Test
    fun freshInstallRunsTutorialThenHome() {
        assertEquals(listOf(StartStep.TUTORIAL, StartStep.HOME), StartFlow.steps(inputs()))
        assertEquals(StartStep.TUTORIAL, StartFlow.first(inputs()))
    }

    @Test
    fun consentIsNotAStartStep() {
        // consent + ads init are gated by the splash itself, before the first step
        assertEquals(listOf(StartStep.TUTORIAL, StartStep.HOME), StartStep.entries)
    }

    @Test
    fun returningUserGoesStraightToHome() {
        val i = inputs(tutorial = true)
        assertEquals(listOf(StartStep.HOME), StartFlow.steps(i))
        assertEquals(StartStep.HOME, StartFlow.first(i))
    }

    @Test
    fun afterWalksTheFreshSequence() {
        val i = inputs()
        assertEquals(StartStep.HOME, StartFlow.after(StartStep.TUTORIAL, i))
        assertEquals(StartStep.HOME, StartFlow.after(StartStep.HOME, i))
    }

    @Test
    fun staleSnapshotStillResolvesNextStep() {
        // snapshot predates TUTORIAL completing (tutorial = false): after(TUTORIAL) must not return TUTORIAL
        assertEquals(StartStep.HOME, StartFlow.after(StartStep.TUTORIAL, inputs(tutorial = false)))
    }
}
