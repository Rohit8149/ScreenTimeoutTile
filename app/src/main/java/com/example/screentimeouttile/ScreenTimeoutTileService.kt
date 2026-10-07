package com.example.screentimeouttile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ScreenTimeoutTileService : TileService() {

    override fun onClick() {
        super.onClick()
        DebugLogger.log("=== TILE CLICKED ===")

        val prefs = getSharedPreferences("screen_timeout_prefs", Context.MODE_PRIVATE)
        val isActive = prefs.getBoolean("is_caffeine_active", false)
        val currentMs = prefs.getLong("current_caffeine_ms", 300000L)

        // Cycle: OFF -> 5m -> 10m -> 30m -> OFF
        val nextMs = if (!isActive) {
            300000L // 5 mins
        } else {
            when (currentMs) {
                300000L -> 600000L // 10 mins
                600000L -> 1800000L // 30 mins
                else -> 0L // OFF
            }
        }

        if (nextMs == 0L) {
            // Stop the Screen Keeper
            val intent = Intent(this, ScreenTimeoutService::class.java).apply { 
                action = ScreenTimeoutService.ACTION_STOP 
            }
            startService(intent)
        } else {
            // Launch Trampoline to safely start Foreground Service
            val trampolineIntent = Intent(this, TrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(ScreenTimeoutService.EXTRA_TIMEOUT_MS, nextMs)
            }
            @Suppress("DEPRECATION")
            startActivityAndCollapse(trampolineIntent)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileUI()
    }

    private fun updateTileUI() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences("screen_timeout_prefs", Context.MODE_PRIVATE)
        val isActive = prefs.getBoolean("is_caffeine_active", false)
        val currentMs = prefs.getLong("current_caffeine_ms", 300000L)

        if (isActive) {
            val mins = currentMs / 60000
            tile.state = Tile.STATE_ACTIVE
            tile.label = "${mins}m (Temp)"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_timer)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Screen Keeper"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_timer)
        }

        tile.updateTile()
    }

    companion object {
        fun requestTileUpdate(context: Context) {
            val componentName = ComponentName(context, ScreenTimeoutTileService::class.java)
            requestListeningState(context, componentName)
        }
    }
}
