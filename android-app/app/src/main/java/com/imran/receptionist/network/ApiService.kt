package com.imran.receptionist.network

import android.content.Context
import android.util.Log
import com.imran.receptionist.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface ApiService {
    @POST("/call-followup")
    suspend fun reportCall(@Body request: CallFollowupRequest): retrofit2.Response<CallFollowupResponse>

    companion object {
        fun create(context: Context): ApiService {
            val httpClient = OkHttpClient.Builder()
                .addInterceptor(SecureAuthInterceptor())
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BuildConfig.BACKEND_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient)
                .build()

            return retrofit.create(ApiService::class.java)
        }
    }
}

data class CallFollowupRequest(
    val callerNumber: String,
    val currentStatus: String
)

data class CallFollowupResponse(
    val success: Boolean,
    val eventId: String,
    val whatsappMessageId: String,
    val timestamp: String
)

class SecureAuthInterceptor : Interceptor {
    @OptIn(ExperimentalEncodingApi::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val token = generateAuthToken()
        request.addHeader("Authorization", "Bearer $token")
        return chain.proceed(request.build())
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun generateAuthToken(): String {
        // HMAC token derived from APP_SHARED_SECRET
        // This matches backend verification
        val message = "android-app"
        val key = BuildConfig.APP_SHARED_SECRET.toByteArray()
        val hmac = javax.crypto.Mac.getInstance("HmacSHA256")
        val keySpec = javax.crypto.spec.SecretKeySpec(key, 0, key.size, "HmacSHA256")
        hmac.init(keySpec)
        val result = hmac.doFinal(message.toByteArray())
        return javax.xml.bind.DatatypeConverter.printHexBinary(result).lowercase()
    }
}
