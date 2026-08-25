package com.imran.receptionist.reminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.imran.receptionist.diagnostics.DiagnosticLogger
import com.imran.receptionist.network.ApiService
import com.imran.receptionist.network.CallbackDecisionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallbackApprovalActionReceiver : BroadcastReceiver() {

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

        val requestedTime =
            intent.getStringExtra(
                CallbackApprovalReceiver.EXTRA_REQUESTED_TIME
            )

        when (intent.action) {

            ACTION_ACCEPT -> {
                handleAccept(
                    context = context,
                    phone = phone,
                    name = name,
                    reason = reason,
                    requestedTime = requestedTime
                )
            }

            ACTION_REJECT -> {
                handleReject(
                    context = context,
                    phone = phone
                )
            }

            ACTION_RESCHEDULE -> {
                val rescheduleIntent =
                    Intent(
                        context,
                        CallbackRescheduleActivity::class.java
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

                        putExtra(
                            CallbackApprovalReceiver.EXTRA_REQUESTED_TIME,
                            requestedTime
                        )
                    }

                context.startActivity(
                    rescheduleIntent
                )
            }
        }
    }

    private fun handleAccept(
        context: Context,
        phone: String,
        name: String?,
        reason: String?,
        requestedTime: String?
    ) {
        if (requestedTime.isNullOrBlank()) {
            DiagnosticLogger.log(
                context,
                "CALLBACK_APPROVAL_ERROR",
                "Accept failed: requested time missing"
            )
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response =
                    ApiService.create()
                        .postCallbackDecision(
                            CallbackDecisionRequest(
                                phone_number = phone,
                                decision = "ACCEPT"
                            )
                        )

                val body = response.body()

                if (
                    response.isSuccessful &&
                    body?.success == true
                ) {
                    val finalTime =
                        body.confirmed_callback_time
                            ?: requestedTime

                    val scheduled =
                        ReminderScheduler.scheduleCallback(
                            context = context,
                            phoneNumber = phone,
                            callerName = name,
                            callerReason = reason,
                            callbackTimeIso = finalTime
                        )

                    dismissApproval(
                        context,
                        phone
                    )

                    DiagnosticLogger.log(
                        context,
                        "CALLBACK_ACCEPTED",
                        if (scheduled) {
                            "Accepted and final reminder scheduled"
                        } else {
                            "Accepted but reminder scheduling failed"
                        }
                    )
                } else {
                    DiagnosticLogger.log(
                        context,
                        "CALLBACK_APPROVAL_ERROR",
                        "Accept HTTP ${response.code()}"
                    )
                }

            } catch (e: Exception) {
                DiagnosticLogger.log(
                    context,
                    "CALLBACK_APPROVAL_ERROR",
                    "Accept ${e.javaClass.simpleName}: " +
                        (e.message ?: "Unknown")
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleReject(
        context: Context,
        phone: String
    ) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response =
                    ApiService.create()
                        .postCallbackDecision(
                            CallbackDecisionRequest(
                                phone_number = phone,
                                decision = "REJECT"
                            )
                        )

                val body = response.body()

                if (
                    response.isSuccessful &&
                    body?.success == true
                ) {
                    dismissApproval(
                        context,
                        phone
                    )

                    DiagnosticLogger.log(
                        context,
                        "CALLBACK_REJECTED",
                        "Callback request rejected"
                    )
                } else {
                    DiagnosticLogger.log(
                        context,
                        "CALLBACK_APPROVAL_ERROR",
                        "Reject HTTP ${response.code()}"
                    )
                }

            } catch (e: Exception) {
                DiagnosticLogger.log(
                    context,
                    "CALLBACK_APPROVAL_ERROR",
                    "Reject ${e.javaClass.simpleName}: " +
                        (e.message ?: "Unknown")
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun dismissApproval(
        context: Context,
        phone: String
    ) {
        val manager =
            context.getSystemService(
                NotificationManager::class.java
            )

        manager.cancel(
            CallbackApprovalReceiver.notificationId(
                phone
            )
        )
    }

    companion object {

        const val ACTION_ACCEPT =
            "com.imran.receptionist.CALLBACK_ACCEPT"

        const val ACTION_REJECT =
            "com.imran.receptionist.CALLBACK_REJECT"

        const val ACTION_RESCHEDULE =
            "com.imran.receptionist.CALLBACK_RESCHEDULE"
    }
}
