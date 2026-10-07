package com.example.screentimeouttile

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ScreenTimeoutService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentTimerJob: Job? = null

    // This receiver shuts down the wakelock the exact millisecond the user locks their screen manually,
    // ensuring absolute ZERO background battery drain.
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                DebugLogger.log("User locked screen manually. Shutting down Screen Keeper.")
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "timeout_channel",
                "Screen Keeper",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification for Screen Keeper"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val timeoutMs = intent?.getLongExtra(EXTRA_TIMEOUT_MS, 300000L) ?: 300000L
        val timeoutMins = timeoutMs / 60000

        // 1. Show Ongoing Notification
        val stopIntent = Intent(this, ScreenTimeoutService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "timeout_channel")
            .setSmallIcon(R.drawable.ic_tile_timer)
            .setContentTitle("Screen Keeper: $timeoutMins min")
            .setContentText("Screen will stay on. Tap to cancel.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(101, notification)
        }

        // 2. Acquire Wakelock (Screen Keeper)
        acquireWakelock()

        // 3. Start Timer
        currentTimerJob?.cancel()
        currentTimerJob = serviceScope.launch {
            delay(timeoutMs)
            DebugLogger.log("Screen Keeper timer ($timeoutMins m) expired. Shutting down.")
            stopSelf()
        }

        // Update Tile UI state
        val prefs = getSharedPreferences("screen_timeout_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_caffeine_active", true)
            .putLong("current_caffeine_ms", timeoutMs)
            .apply()
            
        ScreenTimeoutTileService.requestTileUpdate(this)

        return START_STICKY
    }

    private fun acquireWakelock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "ScreenTimeoutTile::CaffeineWakelock"
            )
            wakeLock?.setReferenceCounted(false)
        }
        wakeLock?.acquire()
        DebugLogger.log("Screen Keeper Wakelock acquired.")
    }

    override fun onDestroy() {
        super.onDestroy()
        currentTimerJob?.cancel()
        serviceScope.cancel()
        
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {}

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                DebugLogger.log("Screen Keeper Wakelock released.")
            }
        }
        wakeLock = null

        val prefs = getSharedPreferences("screen_timeout_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_caffeine_active", false).apply()
        ScreenTimeoutTileService.requestTileUpdate(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.example.screentimeouttile.STOP"
        const val EXTRA_TIMEOUT_MS = "TIMEOUT_MS"
    }
}
