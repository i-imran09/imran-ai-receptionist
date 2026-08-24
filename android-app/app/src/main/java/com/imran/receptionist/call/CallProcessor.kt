package com.imran.receptionist.call

import android.content.Context
import android.util.Log
import com.imran.receptionist.database.CallDatabase
import com.imran.receptionist.database.CallHistoryEntity
import com.imran.receptionist.network.ApiService
import com.imran.receptionist.network.CallFollowupRequest
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

        try {
            val response =
                ApiService.create().reportCall(
                    CallFollowupRequest(
                        callerNumber = number,
                        currentStatus = status,
                        eventId = eventId,
                        callTimestamp =
                            callTimestamp,
                        contactDisplayName =
                            contactDisplayName,
                        callResult =
                            callResult,
                        simSlot =
                            simSlot
                    )
                )

            if (response.isSuccessful) {
                dao.markTemplateSent(eventId)

                Log.i(
                    "ImranAI",
                    "WhatsApp follow-up accepted"
                )
            } else {
                Log.e(
                    "ImranAI",
                    "Backend HTTP ${response.code()}"
                )
            }

        } catch (e: Exception) {
            Log.e(
                "ImranAI",
                "Backend call failed",
                e
            )
        }
    }
}
