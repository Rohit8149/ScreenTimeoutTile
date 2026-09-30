package com.example.screentimeouttile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "timeout_channel"
        private const val NOTIFICATION_ID = 101
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Timeout",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing notification when temporary screen timeout is active"
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showOngoingNotification() {
        val intent = Intent(context, ScreenTimeoutService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun cancelNotification() {
        val intent = Intent(context, ScreenTimeoutService::class.java)
        context.stopService(intent)
    }
}
