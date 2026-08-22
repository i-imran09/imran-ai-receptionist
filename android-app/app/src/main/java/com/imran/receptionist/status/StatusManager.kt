package com.imran.receptionist.status

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "status_prefs")

class StatusManager(private val context: Context) {

    companion object {
        private val STATUS_KEY = stringPreferencesKey("user_status")
        private const val DEFAULT_STATUS = "Work"
    }

    val currentStatus: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[STATUS_KEY] ?: DEFAULT_STATUS
        }

    suspend fun setStatus(status: String) {
        if (!isValidStatus(status)) {
            throw IllegalArgumentException("Invalid status: $status")
        }
        context.dataStore.edit { preferences ->
            preferences[STATUS_KEY] = status
        }
    }

    suspend fun getStatus(): String {
        return try {
            context.dataStore.data.map { preferences ->
                preferences[STATUS_KEY] ?: DEFAULT_STATUS
            }.collect {
                return@getStatus it
            }
            DEFAULT_STATUS
        } catch (e: Exception) {
            DEFAULT_STATUS
        }
    }

    private fun isValidStatus(status: String): Boolean {
        return status in listOf("Work", "Sleep", "Outing")
    }
}
