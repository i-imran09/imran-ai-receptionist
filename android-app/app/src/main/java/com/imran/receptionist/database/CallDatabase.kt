package com.imran.receptionist.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CallHistoryEntity::class], version = 1, exportSchema = false)
abstract class CallDatabase : RoomDatabase() {
    abstract fun callHistoryDao(): CallHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: CallDatabase? = null

        fun getDatabase(context: Context): CallDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CallDatabase::class.java,
                    "imran_calls_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
