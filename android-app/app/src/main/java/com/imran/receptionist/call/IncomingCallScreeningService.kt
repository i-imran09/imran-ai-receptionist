package com.imran.receptionist.call

import android.content.Context
import android.os.Build
import android.telecom.CallScreeningService
import android.util.Log

class IncomingCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: CallDetails) {
        val callerNumber = details.details.handle.schemeSpecificPart
        Log.d("CallScreening", "Call from: $callerNumber")

        // Process the call
        CallProcessor.processCall(applicationContext, callerNumber)

        // Don't block the call
        respondToCall(details, CallResponse.Builder().build())
    }
}
