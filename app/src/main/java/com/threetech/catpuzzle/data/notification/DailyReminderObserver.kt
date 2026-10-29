package com.threetech.catpuzzle.data.notification

import com.threetech.catpuzzle.domain.config.RemoteConfigRepository
import com.threetech.catpuzzle.domain.engine.DailyReminderPolicy
import com.threetech.catpuzzle.domain.notification.DailyReminderScheduler
import com.threetech.catpuzzle.domain.repository.SettingsRepository
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the daily 20:00 reminder booked: on app start and on every toggle change. No game-state dependency.
 * Books the next 20:00 while enabled, cancels when the toggle or the Remote Config switch is off.
 */
@Singleton
class DailyReminderObserver @Inject constructor(
    private val settings: SettingsRepository,
    private val remoteConfig: RemoteConfigRepository,
    private val scheduler: DailyReminderScheduler,
) {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            try {
                settings.settings.map { it.dailyReminders }.distinctUntilChanged().collect { on ->
                    val remote = remoteConfig.current().dailyReminderEnabled
                    if (DailyReminderPolicy.shouldSchedule(on, remote)) {
                        scheduler.schedule(DailyReminderPolicy.delayUntilNextFireMs(LocalDateTime.now()))
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
