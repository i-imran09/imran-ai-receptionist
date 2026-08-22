package com.imran.receptionist.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            return
        }

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d("CallStateReceiver", "State: $state, Number: $incomingNumber")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                Log.d("CallStateReceiver", "Incoming call from: $incomingNumber")
                if (context != null && incomingNumber != null) {
                    CallProcessor.processCall(context, incomingNumber)
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d("CallStateReceiver", "Call ended")
            }
        }
    }
}
