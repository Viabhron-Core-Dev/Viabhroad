package com.example.keyboard

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DictionaryWordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<DictionaryWordEntity>)

    @Query("SELECT COUNT(*) FROM dictionary_words")
    suspend fun count(): Int

    @Query("SELECT * FROM dictionary_words WHERE word = :word LIMIT 1")
    suspend fun getExact(word: String): DictionaryWordEntity?

    @Query("SELECT * FROM dictionary_words WHERE word LIKE :prefix || \'%\' ORDER BY frequency DESC LIMIT :limit")
    suspend fun getSuggestions(prefix: String, limit: Int): List<DictionaryWordEntity>
}
