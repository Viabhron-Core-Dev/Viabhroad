package com.example.keyboard

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalDictionaryDao {
    @Query("SELECT * FROM personal_dictionary ORDER BY word ASC")
    fun getAllWords(): Flow<List<PersonalDictionaryItem>>

    @Query("SELECT * FROM personal_dictionary WHERE word LIKE :prefix || '%' ORDER BY frequency DESC LIMIT :limit")
    suspend fun getSuggestions(prefix: String, limit: Int): List<PersonalDictionaryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PersonalDictionaryItem)

    @Delete
    suspend fun delete(item: PersonalDictionaryItem)
}
