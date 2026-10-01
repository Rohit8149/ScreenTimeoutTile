package com.example.screentimeouttile

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    private const val PREFS_NAME = "debug_logs_prefs"
    private const val KEY_LOGS = "logs"
    private const val MAX_LOGS = 100

    private var prefs: SharedPreferences? = null
    
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs?.getString(KEY_LOGS, "") ?: ""
            if (saved.isNotEmpty()) {
                _logs.value = saved.split("|||")
            }
        }
    }

    fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formatted = "[$time] $message"
        
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, formatted) // Add to top
        if (currentLogs.size > MAX_LOGS) {
            currentLogs.removeLast()
        }
        
        _logs.value = currentLogs
        prefs?.edit()?.putString(KEY_LOGS, currentLogs.joinToString("|||"))?.apply()
    }

    fun clearLogs() {
        _logs.value = emptyList()
        prefs?.edit()?.remove(KEY_LOGS)?.apply()
    }
}
