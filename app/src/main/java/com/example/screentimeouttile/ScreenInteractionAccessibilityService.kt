package com.example.screentimeouttile

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class ScreenInteractionAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var touchWatcherOverlay: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        DebugLogger.log("Touch Reset Accessibility Service connected.")
        attachTouchWatcher()
    }

    private fun attachTouchWatcher() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            // 1x1 pixel invisible view
            val view = View(this)
            
            val params = WindowManager.LayoutParams(
                1, // width: 1px
                1, // height: 1px
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }

            @Suppress("ClickableViewAccessibility")
            view.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    ScreenTimeoutService.notifyUserInteraction(isScroll = false)
                }
                false
            }

            windowManager?.addView(view, params)
            touchWatcherOverlay = view
            DebugLogger.log("Global Tap Watcher attached successfully.")
        } catch (e: Exception) {
            DebugLogger.log("Error attaching Tap Watcher: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            ScreenTimeoutService.notifyUserInteraction(isScroll = true)
        }
    }

    override fun onInterrupt() {
        DebugLogger.log("Touch Reset Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        touchWatcherOverlay?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
        }
        touchWatcherOverlay = null
        windowManager = null
        DebugLogger.log("Touch Reset Accessibility Service disconnected.")
    }

    companion object {
        @Volatile
        var isServiceRunning: Boolean = false
            private set
    }
}
