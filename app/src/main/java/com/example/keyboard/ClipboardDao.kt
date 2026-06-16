package com.example.keyboard

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_history ORDER BY isPinned DESC, timestamp DESC")
    fun getAllItemsFlow(): Flow<List<ClipboardItem>>

    @Query("SELECT * FROM clipboard_history ORDER BY isPinned DESC, timestamp DESC")
    fun getAllItems(): List<ClipboardItem>

    @Query("SELECT * FROM clipboard_history WHERE text = :text LIMIT 1")
    fun getItemByText(text: String): ClipboardItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: ClipboardItem): Long

    @Update
    fun update(item: ClipboardItem)

    @Delete
    fun delete(item: ClipboardItem)

    @Query("DELETE FROM clipboard_history WHERE isPinned = 0")
    fun deleteAllUnpinned()

    @Query("DELETE FROM clipboard_history WHERE isPinned = 0 AND timestamp < :timestamp")
    fun deleteOldUnpinned(timestamp: Long)

    @Query("SELECT COUNT(*) FROM clipboard_history WHERE isPinned = 0")
    fun getUnpinnedCount(): Int

    @Query("DELETE FROM clipboard_history WHERE id IN (SELECT id FROM clipboard_history WHERE isPinned = 0 ORDER BY timestamp ASC LIMIT :limit)")
    fun deleteOldestUnpinned(limit: Int)
}
