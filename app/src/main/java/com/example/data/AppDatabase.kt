package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [WordEntity::class, BigramEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "viaboard_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        INSTANCE?.let { database ->
                            CoroutineScope(Dispatchers.IO).launch {
                                populateDatabase(database.wordDao())
                            }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        suspend fun populateDatabase(wordDao: WordDao) {
            commonEnglishWords.forEach { word ->
                wordDao.upsertWord(word)
            }
        }
    }
}
