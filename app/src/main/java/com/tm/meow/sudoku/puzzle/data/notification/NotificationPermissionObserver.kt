package com.tm.meow.sudoku.puzzle.data.notification

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.tm.meow.sudoku.puzzle.di.DefaultDispatcher
import com.tm.meow.sudoku.puzzle.domain.usecase.SyncNotificationPermission
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * On every app foreground (covers "granted later from system settings, then app resumed") compares the OS
 * notification state with the persisted last-known one; a not-granted -> granted transition turns all reminders ON once.
 * Call [start] on the main thread (ProcessLifecycleOwner replays ON_START if already started).
 */
@Singleton
class NotificationPermissionObserver @Inject constructor(
    private val sync: SyncNotificationPermission,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
) {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                scope.launch {
                    try {
                        sync()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        // best effort; Settings resume retries
                    }
                }
            }
        })
    }
}
