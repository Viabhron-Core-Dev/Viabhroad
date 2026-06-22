package com.example.keyboard

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personal_dictionary")
data class PersonalDictionaryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val word: String,
    val shortcut: String? = null,
    val frequency: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)
