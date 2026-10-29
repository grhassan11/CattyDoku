package com.threetech.catpuzzle.data.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.threetech.catpuzzle.domain.config.RemoteConfigRepository
import com.threetech.catpuzzle.domain.engine.StreakReminderPolicy
import com.threetech.catpuzzle.domain.notification.StreakReminderScheduler
import com.threetech.catpuzzle.domain.repository.SettingsRepository
import com.threetech.catpuzzle.domain.repository.StreakRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StreakReminderEntryPoint {
    fun streakSettings(): SettingsRepository
    fun streakRepo(): StreakRepository
    fun streakRemoteConfig(): RemoteConfigRepository
    fun streakNotifier(): StreakReminderNotifier
    fun streakScheduler(): StreakReminderScheduler
}

/** Fire-time check: everything is re-read here, so a player who already won today gets nothing. */
class StreakReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ep = EntryPointAccessors.fromApplication(applicationContext, StreakReminderEntryPoint::class.java)
        try {
            val enabled = ep.streakSettings().settings.first().streakReminders
            val remote = ep.streakRemoteConfig().current().streakReminderEnabled
            val played = ep.streakRepo().observePlayedDays().first()
            val now = LocalDateTime.now()
            val today = now.toLocalDate().toEpochDay()

            if (StreakReminderPolicy.shouldNotify(played, today, now.hour * 60 + now.minute, enabled, remote)) {
                ep.streakNotifier().show(StreakReminderPolicy.atRiskStreak(played, today), today)
            }
            // Book tomorrow only while a streak is alive; a dead streak stops the chain (a later win restarts it).
            if (StreakReminderPolicy.shouldSchedule(played, today, enabled, remote)) {
                ep.streakScheduler().schedule(StreakReminderPolicy.delayUntilNextFireMs(now, playedToday = true), afterRun = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // best effort: the app-start observer re-books on next launch
        }
        return Result.success()
    }
}
