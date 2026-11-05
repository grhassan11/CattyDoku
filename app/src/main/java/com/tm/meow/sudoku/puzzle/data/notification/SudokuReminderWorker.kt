package com.tm.meow.sudoku.puzzle.data.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tm.meow.sudoku.puzzle.domain.engine.SudokuReminderPolicy
import com.tm.meow.sudoku.puzzle.domain.notification.NotificationStatus
import com.tm.meow.sudoku.puzzle.domain.repository.SettingsRepository
import com.tm.meow.sudoku.puzzle.domain.sudoku.repository.SudokuRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SudokuReminderEntryPoint {
    fun sudokuReminderSettings(): SettingsRepository
    fun sudokuReminderRepo(): SudokuRepository
    fun sudokuReminderStatus(): NotificationStatus
    fun sudokuReminderNotifier(): SudokuReminderNotifier
}

/** Fire-time check: everything is re-read here; a finished / discarded game or a toggled-off setting posts nothing. */
class SudokuReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ep = EntryPointAccessors.fromApplication(applicationContext, SudokuReminderEntryPoint::class.java)
        try {
            val enabled = ep.sudokuReminderSettings().settings.first().sudokuReminders
            val hasGame = ep.sudokuReminderRepo().observeActiveTiers().first().isNotEmpty()
            val now = LocalDateTime.now()
            val minute = now.hour * 60 + now.minute
            if (SudokuReminderPolicy.shouldNotify(enabled, ep.sudokuReminderStatus().areEnabled(), hasGame, minute)) {
                ep.sudokuReminderNotifier().show(now.toLocalDate().toEpochDay())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // best effort: skip silently
        }
        return Result.success()
    }
}
