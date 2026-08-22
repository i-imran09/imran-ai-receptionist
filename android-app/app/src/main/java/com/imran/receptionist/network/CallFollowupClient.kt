package com.imran.receptionist.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CallFollowupClient(
    private val context: Context,
    private val backendUrl: String = "http://192.168.1.100:3000",
    private val secretKey: String = "dev_secret_change_in_production"
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun sendCallFollowup(
        callerNumber: String,
        currentStatus: String
    ): Result {
        return try {
            val jsonBody = JSONObject().apply {
                put("callerNumber", callerNumber)
                put("currentStatus", currentStatus)
                put("callTimestamp", System.currentTimeMillis())
            }

            val requestBody = jsonBody.toString()
                .toRequestBody(okhttp3.MediaType.parse("application/json"))

            val request = Request.Builder()
                .url("$backendUrl/api/call-followup")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $secretKey")
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                Log.d("CallFollowupClient", "Success: $body")
                Result.Success(body)
            } else {
                Log.e("CallFollowupClient", "Error: ${response.code} ${response.message}")
                Result.Error("HTTP ${response.code}: ${response.message}")
            }
        } catch (e: Exception) {
            Log.e("CallFollowupClient", "Network error", e)
            Result.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getHealthStatus(): Result {
        return try {
            val request = Request.Builder()
                .url("$backendUrl/health")
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                Result.Success(body)
            } else {
                Result.Error("HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    sealed class Result {
        data class Success(val data: String) : Result()
        data class Error(val message: String) : Result()
    }
}
