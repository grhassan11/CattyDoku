package com.tm.meow.sudoku.puzzle.data.notification

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tm.meow.sudoku.puzzle.domain.notification.SudokuReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkSudokuReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SudokuReminderScheduler {

    override fun schedule(delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<SudokuReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
    }

    companion object {
        const val UNIQUE_NAME = "sudoku_reminder"
    }
}
