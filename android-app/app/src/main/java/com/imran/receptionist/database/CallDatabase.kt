package com.imran.receptionist.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CallHistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class CallDatabase : RoomDatabase() {

    abstract fun callHistoryDao(): CallHistoryDao

    companion object {

        @Volatile
        private var INSTANCE: CallDatabase? = null

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(
                    database: SupportSQLiteDatabase
                ) {
                    database.execSQL(
                        "ALTER TABLE call_history " +
                        "ADD COLUMN contactDisplayName TEXT"
                    )

                    database.execSQL(
                        "ALTER TABLE call_history " +
                        "ADD COLUMN callResult TEXT NOT NULL " +
                        "DEFAULT 'MISSED'"
                    )

                    database.execSQL(
                        "ALTER TABLE call_history " +
                        "ADD COLUMN simSlot INTEGER NOT NULL " +
                        "DEFAULT 1"
                    )
                }
            }

        fun getDatabase(context: Context): CallDatabase {
            return INSTANCE ?: synchronized(this) {

                val instance =
                    Room.databaseBuilder(
                        context.applicationContext,
                        CallDatabase::class.java,
                        "imran_calls_db"
                    )
                        .addMigrations(MIGRATION_1_2)
                        .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
