package com.imran.receptionist.call

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import android.content.BroadcastReceiver
import android.util.Log
import com.imran.receptionist.contacts.ContactChecker
import com.imran.receptionist.status.StatusRepository
import com.imran.receptionist.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PhoneCallReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PhoneCallReceiver"
    }

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d(TAG, "Call state: $state, Number: $incomingNumber")

        if (state == TelephonyManager.EXTRA_STATE_RINGING && !incomingNumber.isNullOrEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                handleIncomingCall(context, incomingNumber)
            }
        }
    }

    private suspend fun handleIncomingCall(context: Context, phoneNumber: String) {
        try {
            val contactChecker = ContactChecker(context)
            val statusRepository = StatusRepository(context)
            val callHistoryDao = com.imran.receptionist.database.CallDatabase.getDatabase(context).callHistoryDao()

            // Check if contact is saved
            if (contactChecker.isContactSaved(phoneNumber)) {
                Log.d(TAG, "Known contact, ignoring: $phoneNumber")
                return
            }

            Log.d(TAG, "Unknown caller detected: $phoneNumber")

            // Get current status
            var currentStatus = "Work"
            statusRepository.currentStatus.collect { status ->
                currentStatus = status
            }

            // Check for duplicate/recent call
            val recentCall = callHistoryDao.getRecentCall(phoneNumber, System.currentTimeMillis() - 300000) // 5 min
            if (recentCall != null) {
                Log.d(TAG, "Duplicate call within 5 minutes, skipping backend call")
                callHistoryDao.incrementCallCount(phoneNumber)
                return
            }

            // Store locally
            val callEvent = com.imran.receptionist.database.CallHistoryEntity(
                callerNumber = phoneNumber,
                callerName = contactChecker.getContactName(phoneNumber),
                currentStatus = currentStatus,
                callTimestamp = System.currentTimeMillis(),
                callCount = 1
            )
            callHistoryDao.insert(callEvent)

            // Send to backend
            val apiClient = ApiClient(context)
            apiClient.sendCallFollowup(phoneNumber, currentStatus)
        } catch (error: Exception) {
            Log.e(TAG, "Error handling call", error)
        }
    }
}
