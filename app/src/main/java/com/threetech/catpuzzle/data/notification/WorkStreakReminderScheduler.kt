package com.threetech.catpuzzle.data.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.threetech.catpuzzle.domain.notification.StreakReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkStreakReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : StreakReminderScheduler {

    override fun schedule(delayMs: Long, afterRun: Boolean) {
        val request = OneTimeWorkRequestBuilder<StreakReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        // Self-reschedule must not REPLACE (that would cancel the running worker); APPEND_OR_REPLACE queues behind it.
        val policy = if (afterRun) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, policy, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

    companion object {
        const val UNIQUE_NAME = "streak_reminder"
    }
}
