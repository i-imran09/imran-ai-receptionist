package com.imran.receptionist.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiClient(
    private val context: Context,
    private val backendUrl: String = "https://imran-ai-receptionist.onrender.com",
    private val secretKey: String = "dev_secret_change_in_production"
) {
    companion object {
        private const val TAG = "ApiClient"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun sendCallFollowup(phoneNumber: String, status: String) = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("callerNumber", phoneNumber)
                put("currentStatus", status)
                put("callTimestamp", System.currentTimeMillis().toString())
            }

            val mediaType = "application/json".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$backendUrl/call-followup")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $secretKey")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            Log.d(TAG, "Call followup sent: ${response.code}")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending call followup", e)
        }
    }

    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url("$backendUrl/health")
                .get()
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Health check failed", e)
            false
        }
    }
}
