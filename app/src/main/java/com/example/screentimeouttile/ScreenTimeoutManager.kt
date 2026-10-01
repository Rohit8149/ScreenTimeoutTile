package com.example.screentimeouttile

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings

class ScreenTimeoutManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("screen_timeout_prefs", Context.MODE_PRIVATE)

    init {
        DebugLogger.init(context)
    }

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
        val hasPerm = Settings.System.canWrite(context)
        DebugLogger.log("Permission check: $hasPerm")
        return hasPerm
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
            val system = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
            DebugLogger.log("Read System Timeout: $system")
            system
        } catch (e: Settings.SettingNotFoundException) {
            DebugLogger.log("System Timeout NOT FOUND, default 60k")
            60000
        }
    }

    fun enableTemporaryMode(temporaryMs: Int = TEMPORARY_TIMEOUTS_MS[0]) {
        DebugLogger.log("enableTemporaryMode called: $temporaryMs")
        if (!hasWriteSettingsPermission()) {
            DebugLogger.log("Missing permission, aborting.")
            return
        }

        val currentTimeout = getCurrentSystemTimeout()

        prefs.edit()
            .putBoolean(KEY_IS_TEMPORARY_MODE, true)
            .putInt(KEY_ORIGINAL_TIMEOUT, currentTimeout)
            .putInt(KEY_CURRENT_TEMPORARY_TIMEOUT, temporaryMs)
            .apply()

        DebugLogger.log("Saved original: $currentTimeout, enabling temp: $temporaryMs")
        setSystemTimeout(temporaryMs)
    }

    fun setTemporaryTimeout(temporaryMs: Int) {
        DebugLogger.log("setTemporaryTimeout: $temporaryMs")
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
        DebugLogger.log("restoreOriginalTimeout called")
        if (!hasWriteSettingsPermission()) return

        val original = getOriginalTimeout()
        DebugLogger.log("Restoring original: $original")
        setSystemTimeout(original)

        prefs.edit()
            .putBoolean(KEY_IS_TEMPORARY_MODE, false)
            .apply()
    }

    private fun setSystemTimeout(timeoutMs: Int) {
        DebugLogger.log("setSystemTimeout -> attempting to write: $timeoutMs")
        try {
            val success = Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs)
            val readBack = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)
            
            val statusMsg = if (!success) {
                DebugLogger.log("CRITICAL: putInt returned false (blocked by OS)")
                "Failed: OS blocked write."
            } else if (readBack != timeoutMs) {
                DebugLogger.log("CRITICAL: OS instantly overwrote to $readBack")
                "Failed: OS overwrote it instantly (Read $readBack)."
            } else {
                DebugLogger.log("SUCCESS: Readback matches $timeoutMs")
                "Screen timeout set to ${timeoutMs / 60000}m"
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    context,
                    statusMsg,
                    android.widget.Toast.LENGTH_LONG
                ).show()
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
