package com.example.logkeeper

import android.content.Context
import android.util.Log
import com.example.logkeeper.data.LogDatabase
import com.example.logkeeper.data.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TheLogKeeper private constructor(private val context: Context) {

    val logDao = LogDatabase.getDatabase(context).logDao()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs = context.getSharedPreferences("log_keeper_prefs", Context.MODE_PRIVATE)

    var isMasterSwitchOn: Boolean
        get() = prefs.getBoolean("master_switch", true)
        set(value) {
            prefs.edit().putBoolean("master_switch", value).apply()
        }

    fun log(type: String, component: String, message: String, throwable: Throwable? = null) {
        if (!isMasterSwitchOn) return

        val stackTrace = throwable?.stackTraceToString()
        val entry = LogEntry(
            type = type,
            component = component,
            message = message,
            stackTrace = stackTrace
        )

        scope.launch {
            try {
                logDao.insertLog(entry)
            } catch (e: Exception) {
                Log.e("TheLogKeeper", "Failed to insert log entry", e)
            }
        }
    }

    suspend fun getLogsSince(timeFrameMs: Long): List<LogEntry> {
        val since = System.currentTimeMillis() - timeFrameMs
        return logDao.getLogsSince(since)
    }

    suspend fun getAllLogsStatic(): List<LogEntry> {
        return logDao.getLogsSince(0)
    }

    fun exportLogsToDownloads() {
        scope.launch {
            try {
                val logs = getAllLogsStatic()
                val sb = java.lang.StringBuilder()
                for (log in logs) {
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp))
                    sb.append("[$date] ${log.type} / ${log.component}\n")
                    sb.append("${log.message}\n")
                    if (log.stackTrace != null) {
                        sb.append("${log.stackTrace}\n")
                    }
                    sb.append("----------------------------\n")
                }
                
                val filename = "Viaboard_Logs_${System.currentTimeMillis()}.txt"
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                
                // Using MediaStore for Android 10+ 
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(sb.toString().toByteArray())
                    }
                    
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Logs saved to Downloads folder", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TheLogKeeper", "Failed to export logs", e)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: TheLogKeeper? = null

        fun getInstance(context: Context): TheLogKeeper {
            return INSTANCE ?: synchronized(this) {
                val instance = TheLogKeeper(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        // Convenience method if initialized, else logs to logcat
        fun i(type: String, component: String, message: String) {
            INSTANCE?.log(type, component, message) ?: Log.i(component, message)
        }

        fun e(type: String, component: String, message: String, throwable: Throwable? = null) {
            INSTANCE?.log(type, component, message, throwable) ?: Log.e(component, message, throwable)
        }
    }
}
