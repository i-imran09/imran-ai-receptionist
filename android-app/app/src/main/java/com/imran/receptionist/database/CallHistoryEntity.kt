package com.imran.receptionist.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "call_history")
data class CallHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callerNumber: String,
    val currentStatus: String,
    val callTimestamp: Long,
    val eventId: String,
    val callCount: Int = 1,
    val templateSent: Boolean = false
)

@Dao
interface CallHistoryDao {
    @Insert suspend fun insert(call: CallHistoryEntity)

    @Query("SELECT * FROM call_history ORDER BY callTimestamp DESC LIMIT :limit")
    suspend fun getRecentCalls(limit: Int = 20): List<CallHistoryEntity>

    @Query("SELECT * FROM call_history WHERE callerNumber=:number AND callTimestamp>:since ORDER BY callTimestamp DESC LIMIT 1")
    suspend fun getRecentCall(number: String, since: Long): CallHistoryEntity?

    @Query("UPDATE call_history SET callCount=callCount+1 WHERE callerNumber=:number AND id=(SELECT id FROM call_history WHERE callerNumber=:number ORDER BY callTimestamp DESC LIMIT 1)")
    suspend fun incrementCallCount(number: String)

    @Query("UPDATE call_history SET templateSent=1 WHERE eventId=:eventId")
    suspend fun markTemplateSent(eventId: String)
}
