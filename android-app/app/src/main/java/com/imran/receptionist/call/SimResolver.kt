package com.imran.receptionist.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.imran.receptionist.diagnostics.DiagnosticLogger

object SimResolver {

    /**
     * Returns:
     * 1 = SIM 1
     * 2 = SIM 2
     * null = cannot safely determine
     */

    /**
     * Resolve SIM slot after the call ends using CallLog PHONE_ACCOUNT_ID.
     *
     * This is useful on devices where CallScreeningService gives us
     * a null accountHandle during ringing.
     */
    fun resolveFromCallLogAccountId(
        context: Context,
        phoneAccountId: String?
    ): Int? {

        if (phoneAccountId.isNullOrBlank()) {
            DiagnosticLogger.log(
                context,
                "SIM_MAP",
                "CallLog PHONE_ACCOUNT_ID is empty"
            )
            return null
        }

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            DiagnosticLogger.log(
                context,
                "SIM_MAP",
                "READ_PHONE_STATE not granted"
            )
            return null
        }

        try {
            val telecom =
                context.getSystemService(
                    TelecomManager::class.java
                )

            val telephony =
                context.getSystemService(
                    TelephonyManager::class.java
                )

            val accounts =
                telecom.callCapablePhoneAccounts

            DiagnosticLogger.log(
                context,
                "SIM_MAP",
                "Call-capable phone accounts = ${accounts.size}"
            )

            accounts.forEach { handle ->

                DiagnosticLogger.log(
                    context,
                    "SIM_MAP",
                    "PhoneAccount id=${handle.id}"
                )

                if (handle.id == phoneAccountId) {

                    val subId =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            telephony.getSubscriptionId(handle)
                        } else {
                            SubscriptionManager.INVALID_SUBSCRIPTION_ID
                        }

                    DiagnosticLogger.log(
                        context,
                        "SIM_MAP",
                        "Matched PhoneAccount; subscriptionId=$subId"
                    )

                    if (
                        subId !=
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID
                    ) {
                        val slotIndex =
                            SubscriptionManager.getSlotIndex(
                                subId
                            )

                        DiagnosticLogger.log(
                            context,
                            "SIM_MAP",
                            "Matched slotIndex=$slotIndex"
                        )

                        if (
                            slotIndex !=
                            SubscriptionManager.INVALID_SIM_SLOT_INDEX
                        ) {
                            return slotIndex + 1
                        }
                    }
                }
            }

            /*
             * OEM fallback:
             * some devices may store the subscription ID itself
             * in CallLog PHONE_ACCOUNT_ID.
             */
            val numericId =
                phoneAccountId.toIntOrNull()

            if (numericId != null) {

                val manager =
                    context.getSystemService(
                        SubscriptionManager::class.java
                    )

                val active =
                    manager.activeSubscriptionInfoList
                        ?: emptyList()

                val matched =
                    active.firstOrNull {
                        it.subscriptionId == numericId
                    }

                if (matched != null) {

                    val slot =
                        matched.simSlotIndex + 1

                    DiagnosticLogger.log(
                        context,
                        "SIM_MAP",
                        "Numeric subscription fallback -> SIM $slot"
                    )

                    return slot
                }
            }

            DiagnosticLogger.log(
                context,
                "SIM_MAP",
                "No safe CallLog account mapping found"
            )

        } catch (e: SecurityException) {

            DiagnosticLogger.log(
                context,
                "SIM_MAP",
                "SecurityException: ${e.message}"
            )

        } catch (e: Exception) {

            DiagnosticLogger.log(
                context,
                "SIM_MAP",
                "${e.javaClass.simpleName}: ${e.message}"
            )
        }

        return null
    }

    fun resolveSimSlot(
        context: Context,
        accountHandle: PhoneAccountHandle?
    ): Int? {

        if (accountHandle == null) {
            DiagnosticLogger.log(
                context,
                "SIM_DEBUG",
                "PhoneAccountHandle = NULL"
            )
            return null
        }

        val accountId = accountHandle.id

        DiagnosticLogger.log(
            context,
            "SIM_DEBUG",
            "PhoneAccountHandle.id = $accountId"
        )

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            DiagnosticLogger.log(
                context,
                "SIM_DEBUG",
                "READ_PHONE_STATE not granted"
            )
            return null
        }

        /*
         * METHOD 1
         * Android 11+ official PhoneAccountHandle ->
         * subscription mapping.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            try {
                val telephony =
                    context.getSystemService(
                        TelephonyManager::class.java
                    )

                val subscriptionId =
                    telephony.getSubscriptionId(
                        accountHandle
                    )

                DiagnosticLogger.log(
                    context,
                    "SIM_DEBUG",
                    "Telephony subscriptionId = $subscriptionId"
                )

                if (
                    subscriptionId !=
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID
                ) {

                    val slotIndex =
                        SubscriptionManager.getSlotIndex(
                            subscriptionId
                        )

                    DiagnosticLogger.log(
                        context,
                        "SIM_DEBUG",
                        "Method1 slotIndex = $slotIndex"
                    )

                    if (
                        slotIndex !=
                        SubscriptionManager.INVALID_SIM_SLOT_INDEX
                    ) {
                        return slotIndex + 1
                    }
                }

            } catch (e: Exception) {

                DiagnosticLogger.log(
                    context,
                    "SIM_DEBUG",
                    "Method1 failed: ${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }

        /*
         * METHOD 2
         *
         * Inspect active subscriptions.
         * We DON'T guess yet.
         *
         * This tells us exactly what this phone exposes,
         * so the next resolver can safely match SIM1/SIM2.
         */
        try {

            val manager =
                context.getSystemService(
                    SubscriptionManager::class.java
                )

            val subscriptions =
                manager.activeSubscriptionInfoList

            if (subscriptions.isNullOrEmpty()) {

                DiagnosticLogger.log(
                    context,
                    "SIM_DEBUG",
                    "Active subscription list is EMPTY"
                )

                return null
            }

            DiagnosticLogger.log(
                context,
                "SIM_DEBUG",
                "Active subscriptions = ${subscriptions.size}"
            )

            subscriptions.forEach { info ->

                DiagnosticLogger.log(
                    context,
                    "SIM_DEBUG",
                    "subId=${info.subscriptionId}, " +
                        "slotIndex=${info.simSlotIndex}, " +
                        "carrier=${info.carrierName}, " +
                        "display=${info.displayName}"
                )
            }

            /*
             * Some Android/OEM implementations expose the
             * subscription ID directly as PhoneAccountHandle.id.
             */
            val numericAccountId =
                accountId.toIntOrNull()

            if (numericAccountId != null) {

                val match =
                    subscriptions.firstOrNull {
                        it.subscriptionId ==
                            numericAccountId
                    }

                if (match != null) {

                    val slot =
                        match.simSlotIndex + 1

                    DiagnosticLogger.log(
                        context,
                        "SIM_DEBUG",
                        "Method2 exact subscription match -> SIM $slot"
                    )

                    return slot
                }
            }

            /*
             * Do NOT infer SIM1 merely because mapping failed.
             */
            DiagnosticLogger.log(
                context,
                "SIM_DEBUG",
                "No safe account-to-subscription match"
            )

        } catch (e: SecurityException) {

            DiagnosticLogger.log(
                context,
                "SIM_DEBUG",
                "Subscription permission error: ${e.message}"
            )

        } catch (e: Exception) {

            DiagnosticLogger.log(
                context,
                "SIM_DEBUG",
                "Subscription inspection failed: ${e.javaClass.simpleName}: ${e.message}"
            )
        }

        return null
    }
}
