package com.example.screentimeouttile

import android.app.Activity
import android.content.Intent
import android.os.Build

class TrampolineActivity : Activity() {

    override fun onResume() {
        super.onResume()
        DebugLogger.log("+++ TrampolineActivity RESUMED +++")
        
        val nextMs = intent.getLongExtra(ScreenTimeoutService.EXTRA_TIMEOUT_MS, 300000L)
        
        val serviceIntent = Intent(this, ScreenTimeoutService::class.java).apply {
            putExtra(ScreenTimeoutService.EXTRA_TIMEOUT_MS, nextMs)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
