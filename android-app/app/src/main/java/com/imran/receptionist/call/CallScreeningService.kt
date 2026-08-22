package com.imran.receptionist.call

import android.content.Context
import android.os.Build
import android.telecom.CallScreeningService
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.Q)
class CallScreeningService : CallScreeningService() {

    override fun onScreenCall(request: Call.Details) {
        try {
            val phoneNumber = request.handle?.schemeSpecificPart ?: return

            Log.d("CallScreeningService", "Screening call from: $phoneNumber")

            // Check if contact is saved
            val contactChecker = com.imran.receptionist.contacts.ContactChecker(
                applicationContext
            )
            if (contactChecker.isContactSaved(phoneNumber)) {
                Log.d("CallScreeningService", "Known contact, allowing: $phoneNumber")
                respondToCall(request, CallResponse.Builder()
                    .setDisconnectCall(false)
                    .build())
                return
            }

            Log.d("CallScreeningService", "Unknown caller, processing: $phoneNumber")

            // Allow the call through but trigger background processing
            respondToCall(request, CallResponse.Builder()
                .setDisconnectCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build())

            // Note: CallScreeningService cannot directly access call audio
            // Cannot record or intercept call content
            // Can only screen/reject based on number

        } catch (e: Exception) {
            Log.e("CallScreeningService", "Error screening call", e)
            // Always allow call on error
            respondToCall(request, CallResponse.Builder()
                .setDisconnectCall(false)
                .build())
        }
    }
}
