package com.example.logkeeper.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Insert
    suspend fun insertLog(logEntry: LogEntry)

    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getLogsSince(since: Long): List<LogEntry>

    @Query("DELETE FROM log_entries")
    suspend fun clearLogs()
}
