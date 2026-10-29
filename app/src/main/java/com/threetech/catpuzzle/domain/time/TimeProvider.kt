package com.threetech.catpuzzle.domain.time

/** Clock seam (testable). Dates are LOCAL dates expressed as epoch days, so DST never shifts a day. */
interface TimeProvider {
    fun nowMillis(): Long

    /** Monotonic millis for measuring durations (immune to wall-clock changes). Defaults to wall clock. */
    fun monotonicMillis(): Long = nowMillis()

    /** Local date today as `LocalDate.toEpochDay()`. */
    fun todayEpochDay(): Long
}
