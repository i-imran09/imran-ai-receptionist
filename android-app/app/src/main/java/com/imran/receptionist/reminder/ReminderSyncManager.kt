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
                // CALLBACK APPROVAL STATE MACHINE
                // --------------------------------------

                if (caller.callback_requested) {

                    when (
                        caller.callback_status
                            ?.uppercase()
                    ) {

                        "WAITING_OWNER" -> {

                            val requestedTime =
                                caller.caller_requested_time
                                    ?: caller.callback_time

                            if (!requestedTime.isNullOrBlank()) {

                                val approvalIntent =
                                    Intent(
                                        context,
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
                                            name
                                        )

                                        putExtra(
                                            ReminderScheduler.EXTRA_REASON,
                                            reason
                                        )

                                        putExtra(
                                            CallbackApprovalReceiver.EXTRA_REQUESTED_TIME,
                                            requestedTime
                                        )
                                    }

                                context.sendBroadcast(
                                    approvalIntent
                                )

                                DiagnosticLogger.log(
                                    context,
                                    "CALLBACK_APPROVAL_SYNC",
                                    "Waiting for owner decision"
                                )
                            }
                        }

                        "CONFIRMED" -> {

                            val confirmedTime =
                                caller.confirmed_callback_time

                            if (!confirmedTime.isNullOrBlank()) {

                                val scheduled =
                                    ReminderScheduler
                                        .scheduleCallback(
                                            context = context,
                                            phoneNumber = phone,
                                            callerName = name,
                                            callerReason = reason,
                                            callbackTimeIso =
                                                confirmedTime
                                        )

                                DiagnosticLogger.log(
                                    context,
                                    "REMINDER_SYNC",
                                    if (scheduled) {
                                        "Confirmed callback reminder scheduled"
                                    } else {
                                        "Confirmed callback reminder skipped"
                                    }
                                )
                            }
                        }

                        "CANCELLED" -> {

                            DiagnosticLogger.log(
                                context,
                                "CALLBACK_CANCELLED_SYNC",
                                "Cancelled callback ignored"
                            )
                        }

                        else -> {

                            DiagnosticLogger.log(
                                context,
                                "CALLBACK_STATE_SYNC",
                                "Callback has no actionable approval state"
                            )
                        }
                    }
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
