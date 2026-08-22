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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "imran_status")

class StatusRepository(private val context: Context) {
    companion object {
        private val STATUS_KEY = stringPreferencesKey("current_status")
        private const val DEFAULT_STATUS = "Work"
    }

    val currentStatus: Flow<String> = context.dataStore.data
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
        if (status !in listOf("Work", "Sleep", "Outing")) {
            throw IllegalArgumentException("Invalid status: $status")
        }
        context.dataStore.edit { preferences ->
            preferences[STATUS_KEY] = status
        }
    }
}
