package com.imran.receptionist.call

import android.telecom.Call
import android.telecom.CallScreeningService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CallScreeningService : android.telecom.CallScreeningService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(details: Call.Details) {
        // Always leave the actual phone call under the user's control.
        respondToCall(details, CallResponse.Builder().build())

        val number = details.handle?.schemeSpecificPart ?: return
        scope.launch { CallProcessor.process(applicationContext, number) }
    }
}
