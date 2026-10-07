package com.example.screentimeouttile

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings

class ScreenTimeoutManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("screen_timeout_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_TEMPORARY_MODE = "is_temporary_mode"
        private const val KEY_ORIGINAL_TIMEOUT = "original_timeout"
        private const val KEY_CURRENT_TEMPORARY_TIMEOUT = "current_temporary_timeout"

        val TEMPORARY_TIMEOUTS_MS = listOf(
            5 * 60 * 1000,   // 5 minutes
            10 * 60 * 1000,  // 10 minutes
            30 * 60 * 1000   // 30 minutes
        )
    }

    fun hasWriteSettingsPermission(): Boolean {
        return Settings.System.canWrite(context)
    }

    fun isTemporaryModeActive(): Boolean {
        return prefs.getBoolean(KEY_IS_TEMPORARY_MODE, false)
    }

    fun getOriginalTimeout(): Int {
        return prefs.getInt(KEY_ORIGINAL_TIMEOUT, 60000)
    }

    fun getCurrentTemporaryTimeout(): Int {
        return prefs.getInt(KEY_CURRENT_TEMPORARY_TIMEOUT, TEMPORARY_TIMEOUTS_MS[0])
    }

    fun getCurrentSystemTimeout(): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        } catch (e: Settings.SettingNotFoundException) {
            60000
        }
    }

    fun enableTemporaryMode(temporaryMs: Int = TEMPORARY_TIMEOUTS_MS[0]) {
        if (!hasWriteSettingsPermission()) return

        val currentTimeout = getCurrentSystemTimeout()

        prefs.edit()
            .putBoolean(KEY_IS_TEMPORARY_MODE, true)
            .putInt(KEY_ORIGINAL_TIMEOUT, currentTimeout)
            .putInt(KEY_CURRENT_TEMPORARY_TIMEOUT, temporaryMs)
            .apply()

        setSystemTimeout(temporaryMs)
    }

    fun setTemporaryTimeout(temporaryMs: Int) {
        if (!hasWriteSettingsPermission()) return

        prefs.edit()
            .putInt(KEY_CURRENT_TEMPORARY_TIMEOUT, temporaryMs)
            .apply()

        setSystemTimeout(temporaryMs)
    }

    fun getNextTemporaryTimeout(): Int? {
        val current = getCurrentTemporaryTimeout()
        val idx = TEMPORARY_TIMEOUTS_MS.indexOf(current)
        return if (idx >= 0 && idx < TEMPORARY_TIMEOUTS_MS.size - 1) {
            TEMPORARY_TIMEOUTS_MS[idx + 1]
        } else {
            null // cycle back to original
        }
    }

    fun restoreOriginalTimeout() {
        if (!hasWriteSettingsPermission()) return

        val original = getOriginalTimeout()
        setSystemTimeout(original)

        prefs.edit()
            .putBoolean(KEY_IS_TEMPORARY_MODE, false)
            .apply()
    }

    private fun setSystemTimeout(timeoutMs: Int) {
        DebugLogger.log("setSystemTimeout -> attempting to write: $timeoutMs")
        try {
            val uri = Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT)
            var finalReadBack = -1
            var writeSuccess = false

            for (attempt in 1..5) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs)
                context.contentResolver.notifyChange(uri, null)
                
                // Wait 100ms for OS to potentially overwrite
                Thread.sleep(100) 
                
                finalReadBack = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)
                
                if (finalReadBack == timeoutMs) {
                    writeSuccess = true
                    DebugLogger.log("Write stuck successfully on attempt $attempt")
                    break
                } else {
                    DebugLogger.log("Attempt $attempt failed: OS maliciously overwrote to $finalReadBack! Fighting back...")
                    Thread.sleep(50) 
                }
            }

            // Force PowerManager to recalculate display timeouts using a micro-wakelock
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                @Suppress("DEPRECATION")
                val wl = pm.newWakeLock(android.os.PowerManager.SCREEN_DIM_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, "ScreenTimeoutTile::RefreshWakelock")
                wl.acquire(100) // 100 milliseconds is enough to trigger a PowerManager refresh
                DebugLogger.log("Wakelock refresh triggered")
            } catch (e: Exception) {
                DebugLogger.log("Wakelock trick failed: ${e.message}")
            }

            val statusMsg = if (!writeSuccess) {
                "Failed to change timeout (OS blocked)"
            } else {
                "Screen timeout set to ${timeoutMs / 60000}m"
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    context,
                    statusMsg,
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }

            // DIAGNOSTIC: Check if OS reverts it slowly after a few seconds
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                kotlinx.coroutines.delay(5000)
                try {
                    val delayedRead = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)
                    if (delayedRead != timeoutMs) {
                        DebugLogger.log("CRITICAL DIAGNOSTIC: OS slowly reverted to $delayedRead after 5s!")
                        android.widget.Toast.makeText(
                            context,
                            "Diagnostic: OS silently reverted to ${delayedRead/60000}m behind our back!",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        DebugLogger.log("DIAGNOSTIC: DB successfully held $timeoutMs for 5s. PowerManager ignored it.")
                        android.widget.Toast.makeText(
                            context,
                            "Diagnostic: DB held 5m, but OS ignored it.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        } catch (e: Exception) {
            DebugLogger.log("EXCEPTION in setSystemTimeout: ${e.message}")
            e.printStackTrace()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    context, 
                    "Error setting timeout: ${e.message}", 
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
