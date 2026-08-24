package com.imran.receptionist.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.util.Log
import com.imran.receptionist.contacts.ContactChecker
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

        val pending =
            PendingCallStore.get(context)
                ?: return

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
            Log.w(
                "ImranAI",
                "No matching CallLog row found"
            )
            PendingCallStore.clear(context)
            return
        }

        PendingCallStore.clear(context)

        when (finalResult.result) {

            "MISSED",
            "REJECTED" -> {

                val contactName =
                    ContactChecker(context)
                        .getContactName(
                            finalResult.rawNumber
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
                        result = result
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
        val result: String
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
