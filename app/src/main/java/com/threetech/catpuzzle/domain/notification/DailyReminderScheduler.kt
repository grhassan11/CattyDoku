package com.threetech.catpuzzle.domain.notification

/** Schedules the single pending Daily Challenge reminder check. Impl lives in data (no Android in domain). */
interface DailyReminderScheduler {
    /** Replace any pending check with one in [delayMs]. [afterRun] = the check books its own next day. */
    fun schedule(delayMs: Long, afterRun: Boolean = false)

    fun cancel()
}
