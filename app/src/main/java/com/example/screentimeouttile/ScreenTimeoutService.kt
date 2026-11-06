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
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ScreenTimeoutService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentTimerJob: Job? = null

    @Volatile
    private var isHoldingWakeLock = false

    @Volatile
    private var lastInteractionTime = 0L

    @Volatile
    private var lastHeartbeatSec = 0L

    private var targetTimeoutMs = 300000L
    private var nativeTimeoutMs = 60000L
    private var wakelockDurationMs = 240000L

    // This receiver shuts down the service the exact millisecond the user locks their screen
    // or when the screen times out naturally, ensuring absolute ZERO background battery drain.
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                DebugLogger.log("Screen turned off (Power button or timeout). Shutting down Screen Keeper.")
                stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        DebugLogger.init(applicationContext)
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

        targetTimeoutMs = intent?.getLongExtra(EXTRA_TIMEOUT_MS, 300000L) ?: 300000L
        val targetMins = targetTimeoutMs / 60000

        // 1. Read Native Screen Timeout
        nativeTimeoutMs = getNativeTimeout(this)
        val nativeSecs = nativeTimeoutMs / 1000

        // 2. Real-Time Math: Total screen time = wakelockDuration + nativeTimeout = targetTimeout
        wakelockDurationMs = maxOf(0L, targetTimeoutMs - nativeTimeoutMs)

        DebugLogger.log("Screen Keeper: Target=${targetMins}m (${targetTimeoutMs / 1000}s), Native=${nativeSecs}s -> Wakelock duration=${wakelockDurationMs / 1000}s, then native dimming for ${nativeSecs}s.")

        // 3. Show Ongoing Notification
        val stopIntent = Intent(this, ScreenTimeoutService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "timeout_channel")
            .setSmallIcon(R.drawable.ic_tile_timer)
            .setContentTitle("Screen Keeper: ${targetMins}m Active")
            .setContentText("Screen stays on. Tap to turn off.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(101, notification)
        }

        // 4. Acquire Wakelock & Start Interaction Timer
        acquireWakelock()
        lastInteractionTime = SystemClock.elapsedRealtime()
        lastHeartbeatSec = 0L

        startCountdownTimer(targetMins, nativeSecs)

        // Update Tile UI state
        val prefs = getSharedPreferences("screen_timeout_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_caffeine_active", true)
            .putLong("current_caffeine_ms", targetTimeoutMs)
            .commit()

        ScreenTimeoutTileService.requestTileUpdate(this)

        return START_STICKY
    }

    private fun startCountdownTimer(targetMins: Long, nativeSecs: Long) {
        currentTimerJob?.cancel()
        currentTimerJob = serviceScope.launch {
            while (isActive) {
                val elapsedSinceTouch = SystemClock.elapsedRealtime() - lastInteractionTime
                val remainingWakelock = wakelockDurationMs - elapsedSinceTouch

                if (remainingWakelock <= 0L) {
                    // Target minus native has elapsed! Release wakelock so native timeout takes over & dims
                    if (isHoldingWakeLock) {
                        releaseWakelock()
                        DebugLogger.log("Wakelock released after ${wakelockDurationMs / 1000}s. Native ${nativeSecs}s timeout & dimming reminder active.")
                    }

                    // If inactivity exceeds targetTimeoutMs by 5 seconds with zero touches,
                    // screen should be off. Shut down service.
                    if (elapsedSinceTouch >= targetTimeoutMs + 5000L) {
                        DebugLogger.log("Target timeout (${targetTimeoutMs / 1000}s) expired. Shutting down Screen Keeper.")
                        stopSelf()
                        break
                    }
                } else {
                    // Within wakelock window
                    if (!isHoldingWakeLock) {
                        acquireWakelock()
                    }
                }

                // Heartbeat log every 10 seconds (resets when touch occurs)
                val elapsedSec = elapsedSinceTouch / 1000
                if (elapsedSec >= lastHeartbeatSec + 10) {
                    lastHeartbeatSec = (elapsedSec / 10) * 10
                    DebugLogger.log("Screen Keeper Heartbeat: Still running (${lastHeartbeatSec}s elapsed)")
                }

                delay(1000)
            }
        }
    }

    fun onUserInteraction() {
        val now = SystemClock.elapsedRealtime()
        val elapsedSinceLast = now - lastInteractionTime
        lastInteractionTime = now
        lastHeartbeatSec = 0L // Reset heartbeat counter back to 0s!

        // If wakelock was released (screen was dimming / in native phase) and user touched,
        // instantly re-acquire wakelock and bring brightness back up!
        if (!isHoldingWakeLock) {
            acquireWakelock()
            DebugLogger.log("Touch detected! Re-acquired Wakelock, un-dimmed screen, reset timer to ${targetTimeoutMs / 60000}m.")
        } else if (elapsedSinceLast >= 5000L) {
            // Only log if at least 5 seconds have passed since the previous logged touch,
            // preventing log clutter when user is actively tapping or scrolling.
            DebugLogger.log("User touch detected -> Reset countdown (${targetTimeoutMs / 60000}m).")
        }
    }

    @Synchronized
    private fun acquireWakelock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "ScreenTimeoutTile::CaffeineWakelock"
            )
            wakeLock?.setReferenceCounted(false)
        }
        if (wakeLock?.isHeld != true) {
            wakeLock?.acquire()
            isHoldingWakeLock = true
            DebugLogger.log("Screen Keeper Wakelock acquired (full brightness).")
        }
    }

    @Synchronized
    private fun releaseWakelock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                DebugLogger.log("Screen Keeper Wakelock released.")
            }
        }
        isHoldingWakeLock = false
    }

    override fun onDestroy() {
        super.onDestroy()
        DebugLogger.log("Screen Keeper Service Destroyed.")
        if (activeService == this) {
            activeService = null
        }
        currentTimerJob?.cancel()
        serviceScope.cancel()

        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {}

        releaseWakelock()
        wakeLock = null

        val prefs = getSharedPreferences("screen_timeout_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_caffeine_active", false).apply()
        ScreenTimeoutTileService.requestTileUpdate(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.example.screentimeouttile.STOP"
        const val EXTRA_TIMEOUT_MS = "TIMEOUT_MS"

        @Volatile
        private var activeService: ScreenTimeoutService? = null

        fun notifyUserInteraction() {
            activeService?.onUserInteraction()
        }

        fun getNativeTimeout(context: Context): Long {
            // 1. Try last_manual_screen_off_timeout (ColorOS / Realme / OnePlus manual setting)
            try {
                val lastManual = Settings.System.getInt(context.contentResolver, "last_manual_screen_off_timeout", -1)
                if (lastManual in 10000..600000) {
                    return lastManual.toLong()
                }
            } catch (e: Exception) {}

            // 2. Try standard Android screen_off_timeout
            try {
                val systemTimeout = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)
                if (systemTimeout in 10000..600000) {
                    return systemTimeout.toLong()
                }
            } catch (e: Exception) {}

            // 3. Try powersave_backup_screenoff_time (if power saver is active)
            try {
                val powerSave = Settings.System.getInt(context.contentResolver, "powersave_backup_screenoff_time", -1)
                if (powerSave in 10000..600000) {
                    return powerSave.toLong()
                }
            } catch (e: Exception) {}

            return 60000L // default fallback 1 minute
        }
    }
}
