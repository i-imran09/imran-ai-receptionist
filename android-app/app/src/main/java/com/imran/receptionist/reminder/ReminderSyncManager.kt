package com.imran.receptionist.reminder

import android.content.Context
import android.content.Intent
import com.imran.receptionist.diagnostics.DiagnosticLogger
import com.imran.receptionist.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ReminderSyncManager {

    suspend fun sync(
        context: Context
    ) = withContext(Dispatchers.IO) {

        try {
            DiagnosticLogger.log(
                context,
                "REMINDER_SYNC",
                "Checking actionable callers"
            )

            val response =
                ApiService.create()
                    .getActionableCallers()

            if (!response.isSuccessful) {
                DiagnosticLogger.log(
                    context,
                    "REMINDER_SYNC_ERROR",
                    "HTTP ${response.code()}"
                )
                return@withContext
            }

            val body =
                response.body()
                    ?: return@withContext

            body.callers.forEach { caller ->

                val phone =
                    caller.phone_number

                val name =
                    caller.caller_name

                val reason =
                    if (
                        caller.emergency &&
                        !caller.emergency_reason.isNullOrBlank()
                    ) {
                        caller.emergency_reason
                    } else {
                        caller.caller_reason
                    }

                // --------------------------------------
                // EMERGENCY -> immediate alert
                // --------------------------------------

                if (caller.emergency) {

                    val intent =
                        Intent(
                            context,
                            ReminderReceiver::class.java
                        ).apply {

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
                                ReminderScheduler.EXTRA_EMERGENCY,
                                true
                            )
                        }

                    context.sendBroadcast(
                        intent
                    )

                    DiagnosticLogger.log(
                        context,
                        "EMERGENCY_SYNC",
                        "Emergency alert triggered"
                    )
                }

                // --------------------------------------
                // CALLBACK -> local scheduled reminder
                // --------------------------------------

                if (
                    caller.callback_requested &&
                    !caller.callback_time.isNullOrBlank()
                ) {

                    val scheduled =
                        ReminderScheduler
                            .scheduleCallback(
                                context = context,
                                phoneNumber = phone,
                                callerName = name,
                                callerReason = reason,
                                callbackTimeIso =
                                    caller.callback_time
                            )

                    DiagnosticLogger.log(
                        context,
                        "REMINDER_SYNC",
                        if (scheduled)
                            "Callback reminder scheduled"
                        else
                            "Callback reminder skipped"
                    )
                }
            }

        } catch (e: Exception) {

            DiagnosticLogger.log(
                context,
                "REMINDER_SYNC_ERROR",
                e.javaClass.simpleName +
                    ": " +
                    (e.message ?: "Unknown")
            )
        }
    }
}
