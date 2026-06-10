package com.example.data

import kotlinx.coroutines.flow.Flow

class WordRepository(private val wordDao: WordDao) {
    fun getSuggestions(prefix: String): Flow<List<WordEntity>> {
        return wordDao.getSuggestions(prefix.lowercase())
    }

    fun getNextWordSuggestions(word1: String): Flow<List<WordEntity>> {
        return wordDao.getNextWordSuggestions(word1.lowercase())
    }

    suspend fun addWord(word: String) {
        if (word.isNotBlank()) {
            wordDao.upsertWord(word)
        }
    }

    suspend fun addBigram(word1: String, word2: String) {
        if (word1.isNotBlank() && word2.isNotBlank()) {
            wordDao.upsertBigram(word1, word2)
        }
    }
}
