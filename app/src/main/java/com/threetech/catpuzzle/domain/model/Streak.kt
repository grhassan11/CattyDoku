package com.threetech.catpuzzle.domain.model

import java.time.DayOfWeek

/** One cell of the Mon-Sun week strip. */
data class WeekDay(
    val epochDay: Long,
    val dayOfWeek: DayOfWeek,
    val played: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
)

/** Streak summary. [week] is always 7 entries, Monday first. */
data class StreakInfo(
    val current: Int,
    val best: Int,
    val playedToday: Boolean,
    val week: List<WeekDay>,
)
