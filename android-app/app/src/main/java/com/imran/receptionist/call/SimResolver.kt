package com.imran.receptionist.call

import android.content.Context
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log

object SimResolver {

    /**
     * Returns human SIM slot:
     * 1 = SIM 1
     * 2 = SIM 2
     * null = cannot safely determine
     */
    fun resolveSimSlot(
        context: Context,
        accountHandle: PhoneAccountHandle?
    ): Int? {

        if (accountHandle == null) return null

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w("ImranAI", "Reliable SIM mapping requires Android 11+")
            return null
        }

        return try {
            val telephony =
                context.getSystemService(TelephonyManager::class.java)

            val subscriptionId =
                telephony.getSubscriptionId(accountHandle)

            if (
                subscriptionId ==
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            ) {
                Log.w("ImranAI", "Invalid subscription ID")
                null
            } else {
                val zeroBasedSlot =
                    SubscriptionManager.getSlotIndex(subscriptionId)

                if (
                    zeroBasedSlot ==
                    SubscriptionManager.INVALID_SIM_SLOT_INDEX
                ) {
                    null
                } else {
                    zeroBasedSlot + 1
                }
            }

        } catch (e: SecurityException) {
            Log.e("ImranAI", "SIM permission missing", e)
            null
        } catch (e: Exception) {
            Log.e("ImranAI", "SIM resolution failed", e)
            null
        }
    }
}
