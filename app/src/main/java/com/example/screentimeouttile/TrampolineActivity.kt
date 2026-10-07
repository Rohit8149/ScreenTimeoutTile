package com.example.screentimeouttile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class TrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLogger.init(this)
        DebugLogger.log("+++ TrampolineActivity CREATED +++")
        
        val manager = ScreenTimeoutManager(this)
        val notificationHelper = NotificationHelper(this)

        if (!manager.hasWriteSettingsPermission()) {
            finish()
            return
        }

        if (manager.isTemporaryModeActive()) {
            val next = manager.getNextTemporaryTimeout()
            if (next != null) {
                manager.setTemporaryTimeout(next)
                notificationHelper.showOngoingNotification()
            } else {
                manager.restoreOriginalTimeout()
                notificationHelper.cancelNotification()
            }
        } else {
            manager.enableTemporaryMode()
            notificationHelper.showOngoingNotification()
        }

        GlobalScope.launch(Dispatchers.IO) {
            val update = AppUpdater.checkForUpdate()
            if (update != null) {
                AppUpdater.showUpdateNotification(this@TrampolineActivity, update)
            }
        }

        ScreenTimeoutTileService.requestTileUpdate(this)
        
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
