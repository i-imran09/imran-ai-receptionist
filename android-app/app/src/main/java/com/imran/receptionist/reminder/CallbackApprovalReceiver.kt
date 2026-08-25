package com.imran.receptionist.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.imran.receptionist.MainActivity
import com.imran.receptionist.diagnostics.DiagnosticLogger

class CallbackApprovalReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        val phone =
            intent.getStringExtra(
                ReminderScheduler.EXTRA_PHONE
            ) ?: return

        val name =
            intent.getStringExtra(
                ReminderScheduler.EXTRA_NAME
            )
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        val reason =
            intent.getStringExtra(
                ReminderScheduler.EXTRA_REASON
            )
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        val requestedTime =
            intent.getStringExtra(
                EXTRA_REQUESTED_TIME
            )
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        createChannel(context)

        val displayName =
            name ?: phone

        val body = buildString {
            append("Caller: $displayName")

            if (!reason.isNullOrBlank()) {
                append("\nReason: $reason")
            }

            if (!requestedTime.isNullOrBlank()) {
                append("\nRequested time: $requestedTime")
            }
        }

        val openAppIntent =
            Intent(
                context,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val openAppPending =
            PendingIntent.getActivity(
                context,
                phone.hashCode() + 110,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        fun approvalAction(
            action: String,
            requestOffset: Int
        ): PendingIntent {

            val actionIntent =
                Intent(
                    context,
                    CallbackApprovalActionReceiver::class.java
                ).apply {

                    this.action = action

                    putExtra(
                        ReminderScheduler.EXTRA_PHONE,
                        phone
                    )

                    putExtra(
                        ReminderScheduler.EXTRA_NAME,
                        name
                    )

                    putExtra(
                        ReminderScheduler.EXTRA_REASON,
                        reason
                    )

                    putExtra(
                        EXTRA_REQUESTED_TIME,
                        requestedTime
                    )
                }

            return PendingIntent.getBroadcast(
                context,
                phone.hashCode() + requestOffset,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )
        }

        val acceptPending =
            approvalAction(
                CallbackApprovalActionReceiver.ACTION_ACCEPT,
                120
            )

        val rejectPending =
            approvalAction(
                CallbackApprovalActionReceiver.ACTION_REJECT,
                130
            )

        val reschedulePending =
            approvalAction(
                CallbackApprovalActionReceiver.ACTION_RESCHEDULE,
                140
            )

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setContentTitle(
                    "Callback request"
                )
                .setContentText(
                    "$displayName requested a callback"
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(body)
                )
                .setContentIntent(
                    openAppPending
                )
                .setAutoCancel(false)
                .setOngoing(true)
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setCategory(
                    NotificationCompat.CATEGORY_REMINDER
                )
                .addAction(
                    0,
                    "ACCEPT",
                    acceptPending
                )
                .addAction(
                    0,
                    "REJECT",
                    rejectPending
                )
                .addAction(
                    0,
                    "RESCHEDULE",
                    reschedulePending
                )
                .build()

        val manager =
            context.getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            notificationId(phone),
            notification
        )

        DiagnosticLogger.log(
            context,
            "CALLBACK_APPROVAL",
            "Approval notification shown for $displayName"
        )
    }

    private fun createChannel(
        context: Context
    ) {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val manager =
            context.getSystemService(
                NotificationManager::class.java
            )

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Callback Approval Requests",
                NotificationManager.IMPORTANCE_HIGH
            )

        channel.description =
            "Approve, reject or reschedule caller callback requests"

        manager.createNotificationChannel(
            channel
        )
    }

    companion object {

        const val CHANNEL_ID =
            "callback_approval"

        const val EXTRA_REQUESTED_TIME =
            "caller_requested_time"

        fun notificationId(
            phone: String
        ): Int {
            return (
                phone + "_callback_approval"
            ).hashCode()
        }
    }
}
