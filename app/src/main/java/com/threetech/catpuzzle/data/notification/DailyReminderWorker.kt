package com.threetech.catpuzzle.data.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.threetech.catpuzzle.domain.config.RemoteConfigRepository
import com.threetech.catpuzzle.domain.engine.DailyReminderPolicy
import com.threetech.catpuzzle.domain.notification.DailyReminderScheduler
import com.threetech.catpuzzle.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DailyReminderEntryPoint {
    fun dailySettings(): SettingsRepository
    fun dailyRemoteConfig(): RemoteConfigRepository
    fun dailyNotifier(): DailyReminderNotifier
    fun dailyScheduler(): DailyReminderScheduler
}

/** Fire-time check. Gates re-read here: Settings toggle + Remote Config kill switch (permission checked by the notifier). */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ep = EntryPointAccessors.fromApplication(applicationContext, DailyReminderEntryPoint::class.java)
        try {
            val enabled = ep.dailySettings().settings.first().dailyReminders
            val remote = ep.dailyRemoteConfig().current().dailyReminderEnabled
            val now = LocalDateTime.now()

            if (DailyReminderPolicy.shouldNotify(now.hour * 60 + now.minute, enabled, remote)) {
                ep.dailyNotifier().show(now.toLocalDate().toEpochDay())
            }
            // Always book the next 20:00 while enabled: the chain never ends.
            if (DailyReminderPolicy.shouldSchedule(enabled, remote)) {
                ep.dailyScheduler().schedule(DailyReminderPolicy.delayUntilNextFireMs(now), afterRun = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // best effort: the app-start observer re-books on next launch
        }
        return Result.success()
    }
}
