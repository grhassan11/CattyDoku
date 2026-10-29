package com.threetech.catpuzzle.ui.screens.home

import com.threetech.catpuzzle.domain.engine.DailyAvailability
import com.threetech.catpuzzle.domain.engine.DailyRules
import com.threetech.catpuzzle.domain.model.DailyStatus
import com.threetech.catpuzzle.domain.model.Settings
import com.threetech.catpuzzle.domain.model.StreakCalendar
import com.threetech.catpuzzle.ui.screens.game.SettingsUi

/** Domain -> UI model for the home screen. Pure. */
object HomeUiMapper {
    /** [daily] null (not resolved yet) shows the locked pill until the first emission. */
    fun map(
        level: Int?,
        calendar: StreakCalendar?,
        daily: DailyStatus?,
        settings: Settings,
        rewardPending: Boolean = false,
    ): HomeUiState = HomeUiState(
        levelNumber = level?.coerceAtLeast(1),
        streak = calendar?.info?.current?.coerceAtLeast(0) ?: 0,
        daily = dailyUi(daily),
        settings = SettingsUi(
            music = settings.music,
            sfx = settings.sfx,
            voice = settings.voice,
            haptics = settings.haptics,
            patternMode = settings.patternMode,
            streakReminders = settings.streakReminders,
            dailyReminders = settings.dailyReminders,
        ),
        calendar = calendar?.let { calendarUi(it) },
        streakRewardPending = rewardPending,
    )

    fun dailyUi(daily: DailyStatus?): DailyUi = when (val a = daily?.availability) {
        null -> DailyUi.Locked(DailyRules.UNLOCK_LEVEL)
        is DailyAvailability.Locked -> DailyUi.Locked(a.requiredLevel)
        DailyAvailability.Available -> DailyUi.Available
        DailyAvailability.Done -> DailyUi.Done
    }

    fun calendarUi(c: StreakCalendar): StreakCalendarUi {
        fun dot(played: Boolean, done: Boolean, future: Boolean): DayDotState = when {
            done -> DayDotState.DAILY_DONE
            played -> DayDotState.PLAYED
            future -> DayDotState.FUTURE
            else -> DayDotState.MISSED
        }
        // today not played yet = pending (FUTURE), not missed
        val recent = c.recent.map { DayUi(it.epochDay, dot(it.played, it.dailyDone, future = it.isToday), it.isToday) }
        val doneDays = c.recent.filter { it.dailyDone }.map { it.epochDay }.toSet()
        val week = c.info.week.map {
            DayUi(it.epochDay, dot(it.played, it.epochDay in doneDays, it.isFuture || it.isToday), it.isToday)
        }
        return StreakCalendarUi(
            current = c.info.current.coerceAtLeast(0),
            best = c.info.best.coerceAtLeast(0),
            week = week,
            recent = recent,
            dailyTotal = c.dailyCompletedTotal,
        )
    }
}
