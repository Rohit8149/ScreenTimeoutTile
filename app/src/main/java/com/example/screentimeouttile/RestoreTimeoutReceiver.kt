package com.example.screentimeouttile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RestoreTimeoutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val manager = ScreenTimeoutManager(context)
        if (manager.isTemporaryModeActive()) {
            manager.restoreOriginalTimeout()
            
            val notificationHelper = NotificationHelper(context)
            notificationHelper.cancelNotification()
            
            ScreenTimeoutTileService.requestTileUpdate(context)
        }
    }
}
