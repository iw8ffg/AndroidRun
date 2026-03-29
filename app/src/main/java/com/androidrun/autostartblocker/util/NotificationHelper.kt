package com.androidrun.autostartblocker.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.androidrun.autostartblocker.R
import com.androidrun.autostartblocker.ui.MainActivity

object NotificationHelper {

    const val CHANNEL_ID = "autostart_blocker_channel"
    const val SERVICE_NOTIFICATION_ID = 1001
    const val COMPLETION_NOTIFICATION_ID = 1002

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoStart Blocker Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for AutoStart Blocker background service"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    fun buildServiceNotification(context: Context, message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle("AutoStart Blocker")
            .setContentText(message)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun buildCompletionNotification(context: Context, killedCount: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = if (killedCount > 0) {
            "Blocked $killedCount app(s) from auto-starting."
        } else {
            "No blocked apps were found running."
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_block)
            .setContentTitle("AutoStart Blocker - Complete")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }
}
