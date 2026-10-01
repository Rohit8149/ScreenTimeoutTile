package com.example.screentimeouttile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ScreenTimeoutTileService : TileService() {

    private lateinit var manager: ScreenTimeoutManager
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        manager = ScreenTimeoutManager(this)
        notificationHelper = NotificationHelper(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        if (!manager.hasWriteSettingsPermission()) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        val intent = Intent(this, TrampolineActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        
        tile.icon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_tile_timer)
        
        if (manager.isTemporaryModeActive()) {
            tile.state = Tile.STATE_ACTIVE
            val mins = manager.getCurrentTemporaryTimeout() / 60000
            tile.label = "Timeout: ${mins}m"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Temporary"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Screen Timeout"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val current = manager.getCurrentSystemTimeout() / 60000
                tile.subtitle = "${current}m normal"
            }
        }
        tile.updateTile()
    }

    companion object {
        fun requestTileUpdate(context: Context) {
            TileService.requestListeningState(
                context,
                ComponentName(context, ScreenTimeoutTileService::class.java)
            )
        }
    }
}
