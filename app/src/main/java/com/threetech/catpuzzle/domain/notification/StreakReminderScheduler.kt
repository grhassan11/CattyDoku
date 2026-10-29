package com.threetech.catpuzzle.domain.notification

/** Schedules the single pending streak-at-risk check. Impl lives in data (no Android in domain). */
interface StreakReminderScheduler {
    /**
     * Replace any pending check with one running in [delayMs]. [afterRun] = called by the check itself to book the
     * next day (must not cancel the running check).
     */
    fun schedule(delayMs: Long, afterRun: Boolean = false)

    fun cancel()
}
