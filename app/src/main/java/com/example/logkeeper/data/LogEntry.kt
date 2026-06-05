package com.example.logkeeper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "log_entries")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val component: String,
    val message: String,
    val stackTrace: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
