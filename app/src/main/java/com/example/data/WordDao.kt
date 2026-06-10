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
}
