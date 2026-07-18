package com.example.keyboard

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dictionary_words")
data class DictionaryWordEntity(
    @PrimaryKey val word: String,
    val frequency: Int,
    val source: String // "imported" or "personal"
)
