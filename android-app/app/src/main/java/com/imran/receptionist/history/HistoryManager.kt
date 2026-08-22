package com.imran.receptionist.history

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.io.File

data class CallHistoryItem(
    val id: String,
    val callerNumber: String,
    val callerName: String? = null,
    val currentStatus: String,
    val callTimestamp: Long,
    val templateSent: Boolean = false,
    val whatsappReplied: Boolean = false,
    val repeatCount: Int = 1,
    val previousCalls: List<Long> = emptyList(),
    val conversationHistory: List<String> = emptyList()
)

class HistoryManager(private val context: Context) {

    private val storageDir = File(context.filesDir, "call_history")
    private val gson = Gson()

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
    }

    suspend fun addCallHistory(
        callerNumber: String,
        currentStatus: String,
        callTimestamp: Long = System.currentTimeMillis(),
        callerName: String? = null
    ) {
        try {
            val normalized = normalizeNumber(callerNumber)
            val historyFile = File(storageDir, "$normalized.json")

            val item = if (historyFile.exists()) {
                val existing = gson.fromJson(
                    historyFile.readText(),
                    CallHistoryItem::class.java
                )
                existing.copy(
                    repeatCount = existing.repeatCount + 1,
                    previousCalls = existing.previousCalls + callTimestamp,
                    callTimestamp = callTimestamp
                )
            } else {
                CallHistoryItem(
                    id = "hist_${normalized}_${System.currentTimeMillis()}",
                    callerNumber = callerNumber,
                    callerName = callerName,
                    currentStatus = currentStatus,
                    callTimestamp = callTimestamp
                )
            }

            historyFile.writeText(gson.toJson(item))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getRecentHistory(limit: Int = 10): List<CallHistoryItem> {
        return try {
            storageDir.listFiles()?.filter { it.extension == "json" }
                ?.mapNotNull { file ->
                    try {
                        gson.fromJson(file.readText(), CallHistoryItem::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
                ?.sortedByDescending { it.callTimestamp }
                ?.take(limit)
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getHistoryForNumber(phoneNumber: String): CallHistoryItem? {
        return try {
            val normalized = normalizeNumber(phoneNumber)
            val historyFile = File(storageDir, "$normalized.json")
            if (historyFile.exists()) {
                gson.fromJson(historyFile.readText(), CallHistoryItem::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizeNumber(number: String): String {
        return number.replace(Regex("\\D"), "")
    }
}
