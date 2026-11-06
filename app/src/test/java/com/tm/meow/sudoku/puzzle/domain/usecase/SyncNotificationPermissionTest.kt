package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.model.Settings
import com.tm.meow.sudoku.puzzle.domain.notification.NotificationStatus
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeAppFlagsRepository
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncNotificationPermissionTest {

    private val allOff = Settings(streakReminders = false, dailyReminders = false, sudokuReminders = false)
    private val settings = FakeSettingsRepository(allOff)
    private val flags = FakeAppFlagsRepository()
    private var os = false
    private val sync = SyncNotificationPermission(
        NotificationStatus { os },
        ApplyNotificationPermission(settings, flags),
        flags,
    )

    @Test
    fun notGrantedToGrantedTurnsAllOnOnce() = runTest {
        os = true
        sync()
        val s = settings.settings.first()
        assertTrue(s.streakReminders && s.dailyReminders && s.sudokuReminders)
        assertTrue(flags.flags.first().notificationPermissionGranted)
    }

    @Test
    fun staysGrantedKeepsUserChoice() = runTest {
        os = true
        sync()
        settings.update { it.copy(dailyReminders = false) } // user turns one OFF
        sync()
        sync()
        val s = settings.settings.first()
        assertFalse(s.dailyReminders)
        assertTrue(s.streakReminders)
    }

    @Test
    fun explicitGrantedArgumentWins() = runTest {
        sync(true)
        assertTrue(settings.settings.first().sudokuReminders)
    }

    @Test
    fun grantedToDeniedRecordsStateWithoutRewritingToggles() = runTest {
        os = true
        sync()
        os = false
        sync()
        assertFalse(flags.flags.first().notificationPermissionGranted)
        assertTrue(settings.settings.first().streakReminders)
    }

    @Test
    fun regrantAfterDenyTurnsAllOnAgain() = runTest {
        os = true
        sync()
        settings.update { it.copy(streakReminders = false) }
        os = false
        sync()
        os = true
        sync()
        assertTrue(settings.settings.first().streakReminders)
    }

    @Test
    fun deniedFromStartChangesNothing() = runTest {
        sync()
        assertEquals(allOff, settings.settings.first())
        assertFalse(flags.flags.first().notificationPermissionGranted)
    }
}
