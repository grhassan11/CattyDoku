package com.threetech.catpuzzle.data.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.engine.ReminderCopy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Posts the Daily Challenge reminder on its own meow-sound channel (sound is immutable per channel: bump the id to change). */
@Singleton
class DailyReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val soundUri: Uri = Uri.parse("android.resource://${context.packageName}/${R.raw.sfx_meow}")

    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled(), which covers POST_NOTIFICATIONS
    fun show(epochDay: Long, mentionDaily: Boolean = true) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel()
        val titles = if (mentionDaily) TITLES else PLAY_TITLES
        val bodies = if (mentionDaily) BODIES else PLAY_BODIES
        val i = ReminderCopy.indexFor(epochDay, titles.size)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
        val tap = launch?.let {
            PendingIntent.getActivity(context, 1, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_paw)
            .setContentTitle(context.getString(titles[i]))
            .setContentText(context.getString(bodies[i]))
            .setPriority(NotificationCompat.PRIORITY_HIGH) // pre-O
            .setSound(soundUri) // pre-O; O+ uses the channel sound
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        manager.notify(NOTIFICATION_ID, n)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.daily_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.daily_reminder_channel_desc)
            setSound(
                soundUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder_v2_meow"
        private const val NOTIFICATION_ID = 2002
        private val TITLES = intArrayOf(
            R.string.daily_reminder_title_1, R.string.daily_reminder_title_2, R.string.daily_reminder_title_3,
            R.string.daily_reminder_title_4, R.string.daily_reminder_title_5, R.string.daily_reminder_title_6,
            R.string.daily_reminder_title_7,
        )
        private val PLAY_TITLES = intArrayOf(
            R.string.play_reminder_title_1, R.string.play_reminder_title_2, R.string.play_reminder_title_3,
            R.string.play_reminder_title_4, R.string.play_reminder_title_5, R.string.play_reminder_title_6,
            R.string.play_reminder_title_7, R.string.play_reminder_title_8, R.string.play_reminder_title_9,
            R.string.play_reminder_title_10,
        )
        private val PLAY_BODIES = intArrayOf(
            R.string.play_reminder_body_1, R.string.play_reminder_body_2, R.string.play_reminder_body_3,
            R.string.play_reminder_body_4, R.string.play_reminder_body_5, R.string.play_reminder_body_6,
            R.string.play_reminder_body_7, R.string.play_reminder_body_8, R.string.play_reminder_body_9,
            R.string.play_reminder_body_10,
        )
        private val BODIES = intArrayOf(
            R.string.daily_reminder_body_1, R.string.daily_reminder_body_2, R.string.daily_reminder_body_3,
            R.string.daily_reminder_body_4, R.string.daily_reminder_body_5, R.string.daily_reminder_body_6,
            R.string.daily_reminder_body_7,
        )
    }
}
