package com.imran.receptionist.status

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.statusDataStore by preferencesDataStore("imran_status")

class StatusRepository(private val context: Context) {
    private val key = stringPreferencesKey("current_status")
    val currentStatus: Flow<String> =
        context.statusDataStore.data.map { it[key] ?: "Work" }

    suspend fun getStatus(): String = currentStatus.first()

    suspend fun setStatus(value: String) {
        require(value in setOf(
            "Work",
            "Sleep",
            "Outing",
            "Driving",
            "Meeting",
            "Eating",
            "Travel",
            "Exercise",
            "Personal Work",
            "Family Time",
            "Prayer",
            "Busy",
            "Free"
        ))
        context.statusDataStore.edit { it[key] = value }
    }
}
