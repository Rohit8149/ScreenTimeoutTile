package com.example.screentimeouttile

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class ScreenInteractionAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                ScreenTimeoutService.notifyUserInteraction()
            }
        }
    }

    override fun onInterrupt() {
        DebugLogger.log("Touch Reset Accessibility Service interrupted.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        DebugLogger.log("Touch Reset Accessibility Service connected.")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        DebugLogger.log("Touch Reset Accessibility Service disconnected.")
    }

    companion object {
        @Volatile
        var isServiceRunning: Boolean = false
            private set
    }
}
