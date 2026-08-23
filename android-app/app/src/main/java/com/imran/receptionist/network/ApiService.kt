package com.imran.receptionist.network

import com.imran.receptionist.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class CallFollowupRequest(
    val callerNumber: String,
    val currentStatus: String,
    val eventId: String,
    val callTimestamp: Long
)
data class CallFollowupResponse(
    val success: Boolean,
    val conversationId: String? = null,
    val messageId: String? = null
)

interface ApiService {
    @POST("call-followup")
    suspend fun reportCall(@Body request: CallFollowupRequest): retrofit2.Response<CallFollowupResponse>

    companion object {
        fun create(): ApiService {
            val auth = Interceptor { chain ->
                val b = chain.request().newBuilder()
                if (BuildConfig.APP_CLIENT_TOKEN.isNotBlank()) {
                    b.header("Authorization", "Bearer ${BuildConfig.APP_CLIENT_TOKEN}")
                }
                chain.proceed(b.build())
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(auth)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(BuildConfig.BACKEND_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}
