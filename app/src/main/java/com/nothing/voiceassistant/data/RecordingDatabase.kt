package com.nothing.voiceassistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for storing recordings.
 */
@Database(
    entities = [Recording::class],
    version = 1,
    exportSchema = false
)
abstract class RecordingDatabase : RoomDatabase() {
    
    abstract fun recordingDao(): RecordingDao
    
    companion object {
        private const val DATABASE_NAME = "voice_assistant_db"
        
        @Volatile
        private var INSTANCE: RecordingDatabase? = null
        
        /**
         * Get singleton instance of the database.
         */
        fun getInstance(context: Context): RecordingDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): RecordingDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                RecordingDatabase::class.java,
                DATABASE_NAME
            )
            .fallbackToDestructiveMigration()
            .build()
        }
    }
}
