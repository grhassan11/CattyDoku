package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.model.AppFlags
import com.threetech.catpuzzle.ui.screens.game.FakeAppFlagsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkLevelPlayedTest {

    @Test
    fun movesForward() = runTest {
        val flags = FakeAppFlagsRepository(AppFlags(lastPlayedLevel = 4))
        MarkLevelPlayed(flags)(5)
        assertEquals(5, flags.flags.first().lastPlayedLevel)
    }

    @Test
    fun replayingAnEarlierLevelDoesNotPullTheFurthestLevelBack() = runTest {
        val flags = FakeAppFlagsRepository(AppFlags(lastPlayedLevel = 51))
        MarkLevelPlayed(flags)(3)
        assertEquals(51, flags.flags.first().lastPlayedLevel)
    }

    @Test
    fun sameLevelStaysPut() = runTest {
        val flags = FakeAppFlagsRepository(AppFlags(lastPlayedLevel = 7))
        MarkLevelPlayed(flags)(7)
        assertEquals(7, flags.flags.first().lastPlayedLevel)
    }
}
