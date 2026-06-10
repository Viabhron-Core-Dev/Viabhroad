package com.example.data

import androidx.room.Entity

@Entity(tableName = "bigrams", primaryKeys = ["word1", "word2"])
data class BigramEntity(
    val word1: String,
    val word2: String,
    val frequency: Int = 1
)
