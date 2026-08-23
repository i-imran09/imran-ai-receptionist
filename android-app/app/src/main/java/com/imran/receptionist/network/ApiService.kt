package com.imran.receptionist.network

import com.imran.receptionist.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.Path
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

data class ConversationMessage(
    val role: String?,
    val message: String?,
    val imran_status: String?,
    val created_at: String?
)

data class ConversationItem(
    val phone_number: String,
    val caller_name: String?,
    val message_count: Int,
    val last_message: String?,
    val last_message_at: String?,
    val messages: List<ConversationMessage>
)

data class ConversationsResponse(
    val success: Boolean,
    val caller_count: Int,
    val conversations: List<ConversationItem>
)

data class DatabaseDeleteResponse(
    val success: Boolean,
    val deleted: String? = null,
    val phone_number: String? = null,
    val error: String? = null
)

data class DatabaseStatsResponse(
    val success: Boolean,
    val caller_profiles: Int,
    val conversation_messages: Int,
    val approx_data_bytes: Long,
    val approx_data_kb: Double,
    val approx_data_mb: Double
)

interface ApiService {

    @POST("call-followup")
    suspend fun reportCall(
        @Body request: CallFollowupRequest
    ): retrofit2.Response<CallFollowupResponse>

    @GET("api/conversations")
    suspend fun getConversations():
        retrofit2.Response<ConversationsResponse>

    @GET("api/database/stats")
    suspend fun getDatabaseStats():
        retrofit2.Response<DatabaseStatsResponse>

    @DELETE("api/database/caller/{phoneNumber}")
    suspend fun deleteCaller(
        @Path("phoneNumber") phoneNumber: String
    ): retrofit2.Response<DatabaseDeleteResponse>

    @DELETE("api/database/conversations/{phoneNumber}")
    suspend fun deleteConversation(
        @Path("phoneNumber") phoneNumber: String
    ): retrofit2.Response<DatabaseDeleteResponse>

    @DELETE("api/database/conversations")
    suspend fun clearAllConversations():
        retrofit2.Response<DatabaseDeleteResponse>

    companion object {
        fun create(): ApiService {
            val auth = Interceptor { chain ->
                val builder = chain.request().newBuilder()

                if (BuildConfig.APP_CLIENT_TOKEN.isNotBlank()) {
                    builder.header(
                        "Authorization",
                        "Bearer ${BuildConfig.APP_CLIENT_TOKEN}"
                    )
                }

                chain.proceed(builder.build())
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(auth)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
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
