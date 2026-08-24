package com.imran.receptionist.call

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.imran.receptionist.diagnostics.DiagnosticLogger

class CallScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {

        // Never reject/answer automatically.
        // The actual call stays under Imran's control.
        respondToCall(
            details,
            CallResponse.Builder().build()
        )

        val rawNumber =
            details.handle?.schemeSpecificPart
                ?: return

        val normalized =
            PhoneNormalizer.normalize(rawNumber)

        DiagnosticLogger.log(
            applicationContext,
            "CALL_DETECTED",
            "Incoming call detected"
        )

        if (normalized.length < 10) {
            return
        }

        val simSlot =
            SimResolver.resolveSimSlot(
                applicationContext,
                details.accountHandle
            )

        // STRICT RULE:
        // only confirmed SIM 1 calls are processed.
        // Unknown mapping is ignored to avoid touching SIM 2.
        DiagnosticLogger.log(
            applicationContext,
            "SIM",
            "Resolved SIM slot = ${simSlot ?: "UNKNOWN"}"
        )

        if (simSlot == 2) {
            DiagnosticLogger.log(
                applicationContext,
                "SIM_FILTER",
                "Confirmed SIM 2 call ignored"
            )
            return
        }

        /*
         * SIM may be UNKNOWN on some Android/OEM phones because
         * CallScreeningService can return a null PhoneAccountHandle.
         *
         * Do not send anything yet.
         * Preserve the call temporarily so CallStateReceiver can inspect
         * the final CallLog row after the call ends.
         */
        PendingCallStore.save(
            applicationContext,
            PendingIncomingCall(
                number = normalized,
                phoneAccountId =
                    details.accountHandle?.id,
                startedAt =
                    System.currentTimeMillis(),
                simSlot = simSlot ?: -1
            )
        )

        DiagnosticLogger.log(
            applicationContext,
            "PENDING",
            if (simSlot == 1)
                "Confirmed SIM 1 call saved for final-result check"
            else
                "SIM unknown at screening; saved for CallLog SIM verification"
        )

        Log.i(
            "ImranAI",
            "SIM1 incoming call pending: $normalized"
        )
    }
}
