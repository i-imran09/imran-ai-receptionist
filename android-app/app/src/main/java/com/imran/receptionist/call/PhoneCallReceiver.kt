package com.imran.receptionist.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.imran.receptionist.contacts.ContactChecker
import com.imran.receptionist.status.StatusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PhoneCallReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d("PhoneCallReceiver", "Phone state: $state, Number: $incomingNumber")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!incomingNumber.isNullOrEmpty()) {
                    scope.launch {
                        handleIncomingCall(context, incomingNumber)
                    }
                }
            }
        }
    }

    private suspend fun handleIncomingCall(context: Context, phoneNumber: String) {
        try {
            val contactChecker = ContactChecker(context)
            val statusManager = StatusManager(context)
            val historyManager = com.imran.receptionist.history.HistoryManager(context)

            // Check if caller is in contacts
            if (contactChecker.isContactSaved(phoneNumber)) {
                Log.d("PhoneCallReceiver", "Known contact, ignoring: $phoneNumber")
                return
            }

            Log.d("PhoneCallReceiver", "Unknown caller: $phoneNumber")

            // Get current status
            val status = statusManager.getStatus()

            // Save to history
            historyManager.addCallHistory(
                callerNumber = phoneNumber,
                currentStatus = status,
                callTimestamp = System.currentTimeMillis()
            )

            // Send to backend (would be implemented via CallFollowupClient)
            Log.d("PhoneCallReceiver", "Would send to backend: $phoneNumber with status $status")
        } catch (e: Exception) {
            Log.e("PhoneCallReceiver", "Error handling call", e)
        }
    }
}
