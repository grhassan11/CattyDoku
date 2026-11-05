package com.tm.meow.sudoku.puzzle.data.notification

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.tm.meow.sudoku.puzzle.di.DefaultDispatcher
import com.tm.meow.sudoku.puzzle.domain.engine.SudokuReminderPolicy
import com.tm.meow.sudoku.puzzle.domain.notification.SudokuReminderScheduler
import com.tm.meow.sudoku.puzzle.domain.repository.SettingsRepository
import com.tm.meow.sudoku.puzzle.domain.sudoku.repository.SudokuRepository
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the unfinished-Sudoku reminder booked in step with app state. Books ~2 h ahead (clamped to 09:00-21:00) while
 * the toggle is on, a saved Sudoku game exists AND the app is in the background. Cancels on resume (foreground), win /
 * loss / discard (no saved game left) and toggle off. Process foreground = ProcessLifecycleOwner (ON_START / ON_STOP).
 */
@Singleton
class SudokuReminderObserver @Inject constructor(
    private val settings: SettingsRepository,
    private val sudoku: SudokuRepository,
    private val scheduler: SudokuReminderScheduler,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
) : DefaultLifecycleObserver {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // Cold start counts as foreground until the process lifecycle says otherwise.
    private val foreground = MutableStateFlow(true)

    override fun onStart(owner: LifecycleOwner) {
        foreground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground.value = false
    }

    /** Call once from `Application.onCreate` (main thread). */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            try {
                combine(
                    settings.settings.map { it.sudokuReminders }.distinctUntilChanged(),
                    sudoku.observeActiveTiers().map { it.isNotEmpty() }.distinctUntilChanged(),
                    foreground,
                ) { on, hasGame, fg -> Triple(on, hasGame, fg) }
                    .distinctUntilChanged()
                    .collect { (on, hasGame, fg) ->
                        if (!fg && SudokuReminderPolicy.shouldSchedule(on, hasGame)) {
                            scheduler.schedule(SudokuReminderPolicy.delayMs(LocalDateTime.now()))
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
