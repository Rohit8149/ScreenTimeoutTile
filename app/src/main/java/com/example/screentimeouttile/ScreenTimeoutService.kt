package com.example.screentimeouttile

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.os.Build

class ScreenTimeoutService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val manager = ScreenTimeoutManager(this)
            manager.restoreOriginalTimeout()
            ScreenTimeoutTileService.requestTileUpdate(this)
            stopSelf()
            return START_NOT_STICKY
        }

        val stopIntent = Intent(this, ScreenTimeoutService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val manager = ScreenTimeoutManager(this)
        val currentMins = manager.getCurrentTemporaryTimeout() / 60000

        val notification = NotificationCompat.Builder(this, "timeout_channel")
            .setSmallIcon(R.drawable.ic_tile_timer)
            .setContentTitle("Screen Timeout: $currentMins min")
            .setContentText("Temporary screen timeout is active.")
            .setOngoing(true)
            .setDeleteIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(101, notification)
        }

        return START_STICKY
    }

    companion object {
        const val ACTION_STOP = "com.example.screentimeouttile.STOP"
    }
}
