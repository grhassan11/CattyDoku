package com.tm.meow.sudoku.puzzle.data.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.tm.meow.sudoku.puzzle.domain.notification.NotificationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemNotificationStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationStatus {
    override fun areEnabled(): Boolean = try {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    } catch (t: Throwable) {
        false
    }
}
