package com.example.data

import kotlinx.coroutines.flow.Flow

class WordRepository(private val wordDao: WordDao) {
    fun getSuggestions(prefix: String): Flow<List<WordEntity>> {
        return wordDao.getSuggestions(prefix.lowercase())
    }

    suspend fun addWord(word: String) {
        if (word.isNotBlank()) {
            wordDao.upsertWord(word)
        }
    }
}
