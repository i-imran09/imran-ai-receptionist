package com.imran.receptionist.push

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.imran.receptionist.diagnostics.DiagnosticLogger
import com.imran.receptionist.reminder.CallbackApprovalReceiver
import com.imran.receptionist.reminder.ReminderScheduler

class ImranFirebaseMessagingService :
    FirebaseMessagingService() {

    override fun onNewToken(
        token: String
    ) {
        super.onNewToken(token)

        FcmTokenManager.registerToken(
            applicationContext,
            token
        )

        DiagnosticLogger.log(
            applicationContext,
            "FCM_TOKEN",
            "FCM token refreshed"
        )
    }

    override fun onMessageReceived(
        message: RemoteMessage
    ) {
        super.onMessageReceived(message)

        val data =
            message.data

        val type =
            data["type"]
                ?.trim()
                ?.uppercase()

        DiagnosticLogger.log(
            applicationContext,
            "FCM_MESSAGE",
            "Push received type=${type ?: "UNKNOWN"}"
        )

        when (type) {

            "CALLBACK_APPROVAL" -> {

                val phone =
                    data["phone_number"]
                        ?.trim()
                        .orEmpty()

                if (phone.isBlank()) {
                    DiagnosticLogger.log(
                        applicationContext,
                        "FCM_ERROR",
                        "Callback approval push missing phone number"
                    )
                    return
                }

                val intent =
                    Intent(
                        applicationContext,
                        CallbackApprovalReceiver::class.java
                    ).apply {

                        action =
                            "com.imran.receptionist.CALLBACK_APPROVAL"

                        putExtra(
                            ReminderScheduler.EXTRA_PHONE,
                            phone
                        )

                        putExtra(
                            ReminderScheduler.EXTRA_NAME,
                            data["caller_name"]
                        )

                        putExtra(
                            ReminderScheduler.EXTRA_REASON,
                            data["caller_reason"]
                        )

                        putExtra(
                            CallbackApprovalReceiver.EXTRA_REQUESTED_TIME,
                            data["caller_requested_time"]
                        )
                    }

                sendBroadcast(
                    intent
                )

                DiagnosticLogger.log(
                    applicationContext,
                    "FCM_CALLBACK_APPROVAL",
                    "Approval notification triggered from push"
                )
            }

            else -> {
                DiagnosticLogger.log(
                    applicationContext,
                    "FCM_MESSAGE",
                    "Unhandled push type"
                )
            }
        }
    }
}
