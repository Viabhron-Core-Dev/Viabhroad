package com.example.keyboard

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clipboard_history")
data class ClipboardItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    var isPinned: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
