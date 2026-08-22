package com.imran.receptionist.status

import android.content.Context
import android.content.SharedPreferences

class StatusRepository(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "imran_status",
        Context.MODE_PRIVATE
    )

    suspend fun setStatus(status: String) {
        sharedPreferences.edit().putString("current_status", status).apply()
    }

    suspend fun getStatus(): String {
        return sharedPreferences.getString("current_status", "Work") ?: "Work"
    }
}
