package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE word LIKE :prefix || '%' ORDER BY frequency DESC LIMIT 3")
    fun getSuggestions(prefix: String): Flow<List<WordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: WordEntity)

    @Query("SELECT * FROM words WHERE word = :word")
    suspend fun getWord(word: String): WordEntity?

    @Transaction
    suspend fun upsertWord(wordString: String) {
        val lowerWord = wordString.lowercase()
        val existing = getWord(lowerWord)
        if (existing != null) {
            insertWord(existing.copy(frequency = existing.frequency + 1))
        } else {
            insertWord(WordEntity(lowerWord, 1))
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBigram(bigram: BigramEntity)

    @Query("SELECT * FROM bigrams WHERE word1 = :word1 AND word2 = :word2")
    suspend fun getBigram(word1: String, word2: String): BigramEntity?

    @Query("SELECT word2 as word, frequency FROM bigrams WHERE word1 = :word1 ORDER BY frequency DESC LIMIT 3")
    fun getNextWordSuggestions(word1: String): Flow<List<WordEntity>>

    @Transaction
    suspend fun upsertBigram(word1Str: String, word2Str: String) {
        val w1 = word1Str.lowercase()
        val w2 = word2Str.lowercase()
        val existing = getBigram(w1, w2)
        if (existing != null) {
            insertBigram(existing.copy(frequency = existing.frequency + 1))
        } else {
            insertBigram(BigramEntity(w1, w2, 1))
        }
    }
}
