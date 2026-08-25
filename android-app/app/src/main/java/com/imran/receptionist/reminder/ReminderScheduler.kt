package com.imran.receptionist.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.imran.receptionist.diagnostics.DiagnosticLogger
import java.time.OffsetDateTime

object ReminderScheduler {

    const val EXTRA_PHONE = "phone_number"
    const val EXTRA_NAME = "caller_name"
    const val EXTRA_REASON = "caller_reason"
    const val EXTRA_EMERGENCY = "emergency"

    fun scheduleCallback(
        context: Context,
        phoneNumber: String,
        callerName: String?,
        callerReason: String?,
        callbackTimeIso: String
    ): Boolean {

        val triggerMillis =
            try {
                OffsetDateTime
                    .parse(callbackTimeIso)
                    .toInstant()
                    .toEpochMilli()
            } catch (e: Exception) {

                DiagnosticLogger.log(
                    context,
                    "REMINDER_ERROR",
                    "Invalid callback time"
                )

                return false
            }

        if (
            triggerMillis <=
            System.currentTimeMillis()
        ) {
            DiagnosticLogger.log(
                context,
                "REMINDER_ERROR",
                "Callback time already passed"
            )

            return false
        }

        val alarmManager =
            context.getSystemService(
                AlarmManager::class.java
            )

        val intent =
            Intent(
                context,
                ReminderReceiver::class.java
            ).apply {

                action =
                    "com.imran.receptionist.CALLBACK_REMINDER"

                putExtra(
                    EXTRA_PHONE,
                    phoneNumber
                )

                putExtra(
                    EXTRA_NAME,
                    callerName
                )

                putExtra(
                    EXTRA_REASON,
                    callerReason
                )

                putExtra(
                    EXTRA_EMERGENCY,
                    false
                )
            }

        val requestCode =
            phoneNumber.hashCode()

        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        return try {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {

                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )

                DiagnosticLogger.log(
                    context,
                    "REMINDER",
                    "Callback reminder scheduled with inexact fallback"
                )

            } else {

                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )

                DiagnosticLogger.log(
                    context,
                    "REMINDER",
                    "Exact callback reminder scheduled"
                )
            }

            true

        } catch (e: Exception) {

            DiagnosticLogger.log(
                context,
                "REMINDER_ERROR",
                e.javaClass.simpleName +
                    ": " +
                    (e.message ?: "Unknown")
            )

            false
        }
    }
}
