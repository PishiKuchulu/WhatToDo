package com.example.pfptodo.worker.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.pfptodo.MainActivity
import com.example.pfptodo.R

class NotificationHelper(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Creates the reminder channel when needed. [force] is used at app startup
     * to guarantee the channel exists before any notification is posted.
     */
    fun createNotificationChannel(force: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (force || notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_description)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    fun showNotification(taskId: Long, title: String, content: String) {
        val uri = Uri.parse("wtodo://task/$taskId")
        val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            context,
            (taskId and 0x7fffffff).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            notificationManager.notify((taskId and 0x7fffffff).toInt(), notification)
        } catch (e: SecurityException) {
            // Notifications disabled at OS level — nothing to do.
        }
    }

    fun createBirthdayChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(BIRTHDAY_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    BIRTHDAY_CHANNEL_ID,
                    context.getString(R.string.birthday_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.birthday_notification_channel_description)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    fun showBirthdayNotification(birthdayId: Long, name: String, isEve: Boolean = false) {
        val uri = Uri.parse("wtodo://birthday/$birthdayId")
        val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)

        // Distinct request code range so birthday PendingIntents never collide with tasks.
        val requestCode = (birthdayId and 0x3fffffff).toInt() or (if (isEve) 0x50000000 else 0x40000000)
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, BIRTHDAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                context.getString(
                    if (isEve) R.string.birthday_notification_eve_title else R.string.birthday_notification_title,
                    name
                )
            )
            .setContentText(context.getString(R.string.birthday_notification_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            notificationManager.notify(requestCode, notification)
        } catch (e: SecurityException) {
            // Notifications disabled at OS level — nothing to do.
        }
    }

    companion object {
        const val CHANNEL_ID = "task_reminder_channel"
        const val BIRTHDAY_CHANNEL_ID = "birthday_reminder_channel"
    }
}