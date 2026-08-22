package com.imran.receptionist.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "call_history")
data class CallHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val callerNumber: String,
    val callerName: String? = null,
    val currentStatus: String,
    val callTimestamp: Long,
    val callCount: Int = 1,
    val templateSent: Boolean = false
)

@Dao
interface CallHistoryDao {
    @Insert
    suspend fun insert(call: CallHistoryEntity)

    @Query("SELECT * FROM call_history ORDER BY callTimestamp DESC LIMIT :limit")
    suspend fun getRecentCalls(limit: Int): List<CallHistoryEntity>

    @Query("SELECT * FROM call_history WHERE callerNumber = :number AND callTimestamp > :since LIMIT 1")
    suspend fun getRecentCall(number: String, since: Long): CallHistoryEntity?

    @Query("UPDATE call_history SET callCount = callCount + 1 WHERE callerNumber = :number")
    suspend fun incrementCallCount(number: String)

    @Query("DELETE FROM call_history")
    suspend fun clear()
}
