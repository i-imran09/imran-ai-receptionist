package com.imran.receptionist.call

import android.content.Context
import android.util.Log
import com.imran.receptionist.database.CallDatabase
import com.imran.receptionist.database.CallHistoryEntity
import com.imran.receptionist.diagnostics.DiagnosticLogger
import com.imran.receptionist.status.StatusRepository
import java.util.UUID

object CallProcessor {

    suspend fun processFinal(
        context: Context,
        rawNumber: String,
        contactDisplayName: String?,
        callResult: String,
        simSlot: Int,
        callTimestamp: Long
    ) {

        if (
            callResult != "MISSED" &&
            callResult != "REJECTED"
        ) {
            return
        }

        if (simSlot != 1) {
            return
        }

        val number =
            PhoneNormalizer.normalize(rawNumber)

        if (number.length < 10) {
            return
        }

        val dao =
            CallDatabase
                .getDatabase(context)
                .callHistoryDao()

        val status =
            StatusRepository(context)
                .getStatus()

        val eventId =
            UUID.randomUUID().toString()

        val previousCount =
            dao.countCalls(number)

        DiagnosticLogger.log(
            context,
            "PROCESSOR",
            "Eligible $callResult call accepted for processing"
        )

        dao.insert(
            CallHistoryEntity(
                callerNumber = number,
                contactDisplayName =
                    contactDisplayName,
                callResult = callResult,
                simSlot = simSlot,
                currentStatus = status,
                callTimestamp =
                    callTimestamp,
                eventId = eventId,
                callCount =
                    previousCount + 1
            )
        )

        // Store every call accurately,
        // but avoid duplicate template spam
        // from rapid repeat calls.
        val recentTemplate =
            dao.getRecentTemplateCall(
                number,
                callTimestamp - 120_000
            )

        if (recentTemplate != null) {
            Log.i(
                "ImranAI",
                "Call stored; template suppressed for rapid repeat"
            )
            return
        }

        DiagnosticLogger.log(
            context,
            "SMS",
            "Preparing SIM1 SMS follow-up"
        )

        val smsQueued =
            SmsFollowupSender.send(
                context = context,
                callerNumber = rawNumber,
                contactDisplayName =
                    contactDisplayName
            )

        if (smsQueued) {

            /*
             * Reuse the existing templateSent flag as the
             * generic "follow-up sent" marker for now.
             * This preserves the existing 2-minute
             * duplicate-suppression logic without a DB migration.
             */
            dao.markTemplateSent(eventId)

            DiagnosticLogger.log(
                context,
                "SMS",
                "SMS follow-up queued successfully"
            )

            Log.i(
                "ImranAI",
                "SMS follow-up queued successfully"
            )

        } else {

            DiagnosticLogger.log(
                context,
                "SMS_ERROR",
                "SMS follow-up could not be queued"
            )

            Log.e(
                "ImranAI",
                "SMS follow-up failed"
            )
        }

    }
}
