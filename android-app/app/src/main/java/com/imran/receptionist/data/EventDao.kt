package com.imran.receptionist.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: Event)

    @Query("SELECT * FROM events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE callerNumber = :callerNumber ORDER BY timestamp DESC")
    fun getEventsByNumber(callerNumber: String): Flow<List<Event>>
}
