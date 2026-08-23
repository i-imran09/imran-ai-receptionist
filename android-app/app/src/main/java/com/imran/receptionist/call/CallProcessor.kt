package com.imran.receptionist.call

import android.content.Context
import android.util.Log
import com.imran.receptionist.contacts.ContactChecker
import com.imran.receptionist.database.CallDatabase
import com.imran.receptionist.database.CallHistoryEntity
import com.imran.receptionist.network.ApiService
import com.imran.receptionist.network.CallFollowupRequest
import com.imran.receptionist.status.StatusRepository
import java.util.UUID

object CallProcessor {
    suspend fun process(context: Context, rawNumber: String) {
        val number = PhoneNormalizer.normalize(rawNumber)
        if (number.length < 10) return

        if (ContactChecker(context).isContactSaved(rawNumber)) {
            Log.i("ImranAI", "Saved contact ignored")
            return
        }

        val dao = CallDatabase.getDatabase(context).callHistoryDao()
        val now = System.currentTimeMillis()
        val recent = dao.getRecentCall(number, now - 120_000)
        if (recent != null) {
            dao.incrementCallCount(number)
            Log.i("ImranAI", "Duplicate callback suppressed")
            return
        }

        val status = StatusRepository(context).getStatus()
        val eventId = UUID.randomUUID().toString()
        dao.insert(
            CallHistoryEntity(
                callerNumber = number,
                currentStatus = status,
                callTimestamp = now,
                eventId = eventId
            )
        )

        try {
            val response = ApiService.create().reportCall(
                CallFollowupRequest(number, status, eventId, now)
            )
            if (response.isSuccessful) dao.markTemplateSent(eventId)
            else Log.e("ImranAI", "Backend HTTP ${response.code()}")
        } catch (e: Exception) {
            Log.e("ImranAI", "Backend call failed", e)
        }
    }
}
