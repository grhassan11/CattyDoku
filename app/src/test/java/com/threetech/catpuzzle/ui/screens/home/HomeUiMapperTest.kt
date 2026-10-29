package com.threetech.catpuzzle.ui.screens.home

import com.threetech.catpuzzle.domain.engine.DailyAvailability
import com.threetech.catpuzzle.domain.engine.DailyRules
import com.threetech.catpuzzle.domain.engine.StreakCalendarBuilder
import com.threetech.catpuzzle.domain.model.DailyStatus
import com.threetech.catpuzzle.domain.model.Settings
import com.threetech.catpuzzle.domain.model.StreakCalendar
import com.threetech.catpuzzle.ui.screens.game.SettingToggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiMapperTest {

    private val today = 20_752L

    private fun streak(current: Int): StreakCalendar =
        StreakCalendarBuilder.build(((today - current + 1)..today).toSet(), emptySet(), today)

    private fun daily(a: DailyAvailability) = DailyStatus(today, 0, emptySet(), a)

    @Test
    fun mapsLevelStreakAndSettings() {
        val ui = HomeUiMapper.map(12, streak(5), null, Settings(music = true, sfx = false, voice = false, haptics = true, patternMode = true))
        assertEquals(12, ui.levelNumber)
        assertEquals(5, ui.streak)
        assertTrue(ui.settings.music)
        assertFalse(ui.settings.sfx)
        assertFalse(ui.settings.voice)
        assertTrue(ui.settings.haptics)
        assertTrue(ui.settings.patternMode)
    }

    @Test
    fun missingLevelStaysNullAndMissingStreakIsZero() {
        val ui = HomeUiMapper.map(null, null, null, Settings())
        assertNull(ui.levelNumber)
        assertEquals(0, ui.streak)
    }

    @Test
    fun levelIsClampedToOne() {
        assertEquals(1, HomeUiMapper.map(0, null, null, Settings()).levelNumber)
    }

    @Test
    fun dailyStatesMapToUi() {
        assertEquals(DailyUi.Locked(5), HomeUiMapper.dailyUi(null)) // unresolved = locked pill
        assertEquals(DailyUi.Locked(7), HomeUiMapper.dailyUi(daily(DailyAvailability.Locked(7))))
        assertEquals(DailyUi.Available, HomeUiMapper.dailyUi(daily(DailyAvailability.Available)))
        assertEquals(DailyUi.Done, HomeUiMapper.dailyUi(daily(DailyAvailability.Done)))
        assertEquals(DailyRules.UNLOCK_LEVEL, 5)
    }

    @Test
    fun calendarDotsDistinguishDailyPlayedMissedAndPendingToday() {
        val played = setOf(today - 2, today - 1)
        val ui = HomeUiMapper.calendarUi(StreakCalendarBuilder.build(played, setOf(today - 1), today))
        val byDay = ui.recent.associateBy { it.epochDay }
        assertEquals(DayDotState.DAILY_DONE, byDay.getValue(today - 1).state)
        assertEquals(DayDotState.PLAYED, byDay.getValue(today - 2).state)
        assertEquals(DayDotState.MISSED, byDay.getValue(today - 3).state)
        assertEquals(DayDotState.FUTURE, byDay.getValue(today).state) // today, nothing yet = pending
        assertTrue(byDay.getValue(today).isToday)
        assertEquals(2, ui.current)
        assertEquals(1, ui.dailyTotal)
        assertEquals(7, ui.week.size)
    }

    @Test
    fun withToggleFlipsOnlyThatSetting() {
        for (toggle in SettingToggle.entries) {
            val base = Settings(music = false, sfx = false, voice = false, haptics = false, patternMode = false, streakReminders = false, dailyReminders = false)
            val on = base.withToggle(toggle, true)
            val flipped = listOf(on.music, on.sfx, on.voice, on.haptics, on.patternMode, on.streakReminders, on.dailyReminders).count { it }
            assertEquals("toggle $toggle", 1, flipped)
            assertEquals(base, on.withToggle(toggle, false))
        }
    }

    @Test
    fun patternIsDeterministicAndSparse() {
        var xs = 0
        var total = 0
        for (r in 0 until 20) for (c in 0 until 12) {
            assertEquals(patternHasX(r, c), patternHasX(r, c))
            total++
            if (patternHasX(r, c)) xs++
        }
        assertTrue("some X tiles", xs > 0)
        assertTrue("not every tile", xs < total / 2)
    }

    @Test
    fun patternAlphaWithinSpec() {
        assertTrue(HOME_PATTERN_ALPHA in 0.06f..0.08f)
    }
}
