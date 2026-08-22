package com.imran.receptionist.call

import android.content.Context
import android.os.Build
import android.telecom.InCallService
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.Q)
class IncomingCallScreeningService : InCallService() {
    companion object {
        private const val TAG = "CallScreeningService"
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added: ${call.details?.handle?.schemeSpecificPart}")
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call removed")
    }
}
