package com.imran.receptionist.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.imran.receptionist.diagnostics.DiagnosticLogger
import java.time.OffsetDateTime
import java.time.ZoneId

class ReminderActionReceiver : BroadcastReceiver() {

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

        val reason =
            intent.getStringExtra(
                ReminderScheduler.EXTRA_REASON
            )

        when (intent.action) {

            ACTION_SNOOZE -> {

                val newTime =
                    OffsetDateTime
                        .now(
                            ZoneId.of(
                                "Asia/Kolkata"
                            )
                        )
                        .plusMinutes(10)
                        .toString()

                val scheduled =
                    ReminderScheduler.scheduleCallback(
                        context = context,
                        phoneNumber = phone,
                        callerName = name,
                        callerReason = reason,
                        callbackTimeIso = newTime
                    )

                dismissNotification(
                    context,
                    phone
                )

                DiagnosticLogger.log(
                    context,
                    "REMINDER_SNOOZE",
                    if (scheduled)
                        "Reminder snoozed for 10 minutes"
                    else
                        "Unable to snooze reminder"
                )
            }

            ACTION_CANCEL -> {

                dismissNotification(
                    context,
                    phone
                )

                val cancelIntent =
                    Intent(
                        context,
                        CancelReasonActivity::class.java
                    ).apply {

                        flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP

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
                    }

                context.startActivity(
                    cancelIntent
                )

                DiagnosticLogger.log(
                    context,
                    "REMINDER_CANCEL",
                    "Cancel reason screen opened"
                )
            }
        }
    }

    private fun dismissNotification(
        context: Context,
        phone: String
    ) {

        val manager =
            context.getSystemService(
                NotificationManager::class.java
            )

        manager.cancel(
            ReminderReceiver.notificationId(
                phone,
                false
            )
        )
    }

    companion object {

        const val ACTION_SNOOZE =
            "com.imran.receptionist.REMINDER_SNOOZE"

        const val ACTION_CANCEL =
            "com.imran.receptionist.REMINDER_CANCEL"
    }
}
