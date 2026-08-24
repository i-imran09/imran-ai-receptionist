package com.imran.receptionist.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.imran.receptionist.contacts.ContactChecker
import com.imran.receptionist.diagnostics.DiagnosticLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (
            intent.action !=
            TelephonyManager.ACTION_PHONE_STATE_CHANGED
        ) return

        val state =
            intent.getStringExtra(
                TelephonyManager.EXTRA_STATE
            )

        if (state != TelephonyManager.EXTRA_STATE_IDLE) {
            return
        }

        DiagnosticLogger.log(
            context,
            "CALL_END",
            "Phone state became IDLE"
        )

        val pending =
            PendingCallStore.get(context)

        if (pending == null) {
            DiagnosticLogger.log(
                context,
                "PENDING",
                "No pending SIM 1 call found"
            )
            return
        }

        val appContext =
            context.applicationContext

        scope.launch {
            mutex.withLock {
                finalizeCall(
                    appContext,
                    pending
                )
            }
        }
    }

    private suspend fun finalizeCall(
        context: Context,
        pending: PendingIncomingCall
    ) {

        // Call-log insertion can happen slightly after IDLE.
        var finalResult: FinalCallResult? = null

        for (attempt in 1..6) {
            delay(1000)

            finalResult =
                findFinalResult(
                    context,
                    pending
                )

            if (finalResult != null) {
                break
            }
        }

        if (finalResult == null) {
            DiagnosticLogger.log(
                context,
                "CALL_LOG",
                "No matching call-log entry found"
            )

            Log.w(
                "ImranAI",
                "No matching CallLog row found"
            )

            PendingCallStore.clear(context)
            return
        }

        DiagnosticLogger.log(
            context,
            "CALL_LOG",
            "Final result = ${finalResult.result}"
        )

        DiagnosticLogger.log(
            context,
            "SIM_CALLLOG",
            "PHONE_ACCOUNT_ID = ${finalResult.phoneAccountId ?: "NULL"}"
        )

        try {
            val subscriptionManager =
                context.getSystemService(
                    SubscriptionManager::class.java
                )

            val active =
                subscriptionManager.activeSubscriptionInfoList

            if (active.isNullOrEmpty()) {
                DiagnosticLogger.log(
                    context,
                    "SIM_CALLLOG",
                    "Active subscription list is EMPTY"
                )
            } else {
                active.forEach { info ->
                    DiagnosticLogger.log(
                        context,
                        "SIM_CALLLOG",
                        "subId=${info.subscriptionId}, " +
                            "slot=${info.simSlotIndex + 1}, " +
                            "carrier=${info.carrierName}, " +
                            "display=${info.displayName}"
                    )
                }
            }

        } catch (e: Exception) {
            DiagnosticLogger.log(
                context,
                "SIM_CALLLOG",
                "Subscription inspection failed: " +
                    e.javaClass.simpleName +
                    ": " +
                    (e.message ?: "Unknown")
            )
        }

        PendingCallStore.clear(context)

        if (pending.simSlot != 1) {
            DiagnosticLogger.log(
                context,
                "SIM_VERIFY",
                "SIM was not confirmed as SIM 1. Follow-up blocked until CallLog mapping is verified."
            )

            PendingCallStore.clear(context)
            return
        }

        when (finalResult.result) {

            "MISSED",
            "REJECTED" -> {

                val contactName =
                    ContactChecker(context)
                        .getContactName(
                            finalResult.rawNumber
                        )

                DiagnosticLogger.log(
                    context,
                    "CONTACT",
                    if (contactName.isNullOrBlank())
                        "Caller is not saved in contacts"
                    else
                        "Saved contact name found"
                )

                CallProcessor.processFinal(
                    context = context,
                    rawNumber =
                        finalResult.rawNumber,
                    contactDisplayName =
                        contactName,
                    callResult =
                        finalResult.result,
                    simSlot =
                        pending.simSlot,
                    callTimestamp =
                        finalResult.timestamp
                )
            }

            else -> {
                DiagnosticLogger.log(
                    context,
                    "CALL_FILTER",
                    "No follow-up: result=${finalResult.result}"
                )

                Log.i(
                    "ImranAI",
                    "Answered/non-target call ignored: " +
                        finalResult.result
                )
            }
        }
    }

    private fun findFinalResult(
        context: Context,
        pending: PendingIncomingCall
    ): FinalCallResult? {

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )

        return try {

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 15"
            )?.use { cursor ->

                val numberIndex =
                    cursor.getColumnIndexOrThrow(
                        CallLog.Calls.NUMBER
                    )

                val typeIndex =
                    cursor.getColumnIndexOrThrow(
                        CallLog.Calls.TYPE
                    )

                val dateIndex =
                    cursor.getColumnIndexOrThrow(
                        CallLog.Calls.DATE
                    )

                val accountIndex =
                    cursor.getColumnIndex(
                        CallLog.Calls.PHONE_ACCOUNT_ID
                    )

                while (cursor.moveToNext()) {

                    val rawNumber =
                        cursor.getString(numberIndex)
                            ?: continue

                    val normalized =
                        PhoneNormalizer.normalize(
                            rawNumber
                        )

                    val timestamp =
                        cursor.getLong(dateIndex)

                    // Ignore old call-log rows.
                    if (
                        timestamp <
                        pending.startedAt - 10_000
                    ) {
                        continue
                    }

                    if (
                        normalized != pending.number
                    ) {
                        continue
                    }

                    val accountId =
                        if (accountIndex >= 0)
                            cursor.getString(accountIndex)
                        else
                            null

                    // When both IDs exist, require same SIM account.
                    if (
                        pending.phoneAccountId != null &&
                        accountId != null &&
                        pending.phoneAccountId != accountId
                    ) {
                        continue
                    }

                    val type =
                        cursor.getInt(typeIndex)

                    val result =
                        when (type) {
                            CallLog.Calls.MISSED_TYPE ->
                                "MISSED"

                            CallLog.Calls.REJECTED_TYPE ->
                                "REJECTED"

                            CallLog.Calls.INCOMING_TYPE ->
                                "ANSWERED"

                            else ->
                                "OTHER"
                        }

                    return FinalCallResult(
                        rawNumber = rawNumber,
                        timestamp = timestamp,
                        result = result,
                        phoneAccountId = accountId
                    )
                }

                null
            }

        } catch (e: SecurityException) {
            Log.e(
                "ImranAI",
                "READ_CALL_LOG permission missing",
                e
            )
            null

        } catch (e: Exception) {
            Log.e(
                "ImranAI",
                "CallLog read failed",
                e
            )
            null
        }
    }

    data class FinalCallResult(
        val rawNumber: String,
        val timestamp: Long,
        val result: String,
        val phoneAccountId: String?
    )

    companion object {
        private val scope =
            CoroutineScope(
                SupervisorJob() +
                    Dispatchers.IO
            )

        private val mutex = Mutex()
    }
}
