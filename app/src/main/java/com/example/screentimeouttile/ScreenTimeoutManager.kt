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

    /**
     * Activates temporary mode for the first time.
     * Saves the current system timeout as original, then sets the given temporary timeout.
     */
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

    /**
     * Switches to a different temporary timeout without overwriting the original.
     */
    fun setTemporaryTimeout(temporaryMs: Int) {
        if (!hasWriteSettingsPermission()) return

        prefs.edit()
            .putInt(KEY_CURRENT_TEMPORARY_TIMEOUT, temporaryMs)
            .apply()

        setSystemTimeout(temporaryMs)
    }

    /**
     * Returns the next temporary timeout in the cycle, or null if we should restore original.
     * Cycle: 5m → 10m → 30m → null (restore)
     */
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
        try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs)
            
            // Add visual confirmation
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    context, 
                    "Screen timeout set to ${timeoutMs / 60000}m", 
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
