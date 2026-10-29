package com.threetech.catpuzzle.data.notification

import com.threetech.catpuzzle.domain.config.RemoteConfigRepository
import com.threetech.catpuzzle.domain.engine.StreakReminderPolicy
import com.threetech.catpuzzle.domain.notification.StreakReminderScheduler
import com.threetech.catpuzzle.domain.repository.SettingsRepository
import com.threetech.catpuzzle.domain.repository.StreakRepository
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the reminder booked in step with app state: runs on app start and on every toggle change or played-days change
 * (a win). Books the next 19:00 while a streak is alive, cancels when the streak is 0 or reminders are off.
 */
@Singleton
class StreakReminderObserver @Inject constructor(
    private val settings: SettingsRepository,
    private val streak: StreakRepository,
    private val remoteConfig: RemoteConfigRepository,
    private val scheduler: StreakReminderScheduler,
) {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            try {
                combine(
                    settings.settings.map { it.streakReminders }.distinctUntilChanged(),
                    streak.observePlayedDays(),
                ) { on, days -> on to days }.collect { (on, days) ->
                    val now = LocalDateTime.now()
                    val today = now.toLocalDate().toEpochDay()
                    val remote = remoteConfig.current().streakReminderEnabled
                    if (StreakReminderPolicy.shouldSchedule(days, today, on, remote)) {
                        scheduler.schedule(StreakReminderPolicy.delayUntilNextFireMs(now, today in days))
                    } else {
                        scheduler.cancel()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // reminders are optional; never affect the app
            }
        }
    }
}
