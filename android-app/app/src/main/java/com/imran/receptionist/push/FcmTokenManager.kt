package com.imran.receptionist.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.imran.receptionist.diagnostics.DiagnosticLogger
import com.imran.receptionist.network.ApiService
import com.imran.receptionist.network.DeviceTokenRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object FcmTokenManager {

    fun refreshAndRegister(
        context: Context
    ) {
        FirebaseMessaging
            .getInstance()
            .token
            .addOnCompleteListener { task ->

                if (!task.isSuccessful) {
                    DiagnosticLogger.log(
                        context,
                        "FCM_TOKEN_ERROR",
                        task.exception
                            ?.message
                            ?: "Unable to get FCM token"
                    )
                    return@addOnCompleteListener
                }

                val token =
                    task.result
                        ?.trim()
                        .orEmpty()

                if (token.isBlank()) {
                    DiagnosticLogger.log(
                        context,
                        "FCM_TOKEN_ERROR",
                        "FCM token is blank"
                    )
                    return@addOnCompleteListener
                }

                registerToken(
                    context,
                    token
                )
            }
    }

    fun registerToken(
        context: Context,
        token: String
    ) {
        CoroutineScope(
            Dispatchers.IO
        ).launch {

            try {
                val response =
                    ApiService.create()
                        .registerDeviceToken(
                            DeviceTokenRequest(
                                token = token,
                                platform = "android"
                            )
                        )

                if (
                    response.isSuccessful &&
                    response.body()?.success == true
                ) {
                    DiagnosticLogger.log(
                        context,
                        "FCM_TOKEN",
                        "Device token registered"
                    )
                } else {
                    DiagnosticLogger.log(
                        context,
                        "FCM_TOKEN_ERROR",
                        "Register HTTP ${response.code()}"
                    )
                }

            } catch (e: Exception) {

                DiagnosticLogger.log(
                    context,
                    "FCM_TOKEN_ERROR",
                    e.javaClass.simpleName +
                        ": " +
                        (e.message ?: "Unknown")
                )
            }
        }
    }
}
