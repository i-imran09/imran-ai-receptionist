package com.imran.receptionist.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.imran.receptionist.diagnostics.DiagnosticLogger
import java.net.URLEncoder

object SmsFollowupSender {

    /*
     * WhatsApp destination.
     * International digits only, without + or spaces.
     */
    private const val WHATSAPP_NUMBER = "918110813042"

    fun send(
        context: Context,
        callerNumber: String,
        contactDisplayName: String?
    ): Boolean {

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            DiagnosticLogger.log(
                context,
                "SMS_ERROR",
                "SEND_SMS permission not granted"
            )
            return false
        }

        val subscriptionManager =
            context.getSystemService(
                SubscriptionManager::class.java
            )

        val sim1Subscription =
            try {
                subscriptionManager
                    .activeSubscriptionInfoList
                    ?.firstOrNull {
                        it.simSlotIndex == 0
                    }
            } catch (e: Exception) {
                DiagnosticLogger.log(
                    context,
                    "SMS_ERROR",
                    "Unable to resolve SIM1 subscription: " +
                        e.javaClass.simpleName
                )
                null
            }

        if (sim1Subscription == null) {
            DiagnosticLogger.log(
                context,
                "SMS_ERROR",
                "SIM1 subscription not found"
            )
            return false
        }

        val encodedHi =
            URLEncoder.encode(
                "Hi",
                "UTF-8"
            )

        val whatsappLink =
            "https://wa.me/$WHATSAPP_NUMBER" +
                "?text=$encodedHi"

        val cleanName =
            contactDisplayName
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        val message =
            if (cleanName == null) {
                """
Hi, idhu Imran-oda AI Receptionist. Indha call genuine human call-a confirm panna, keezha irukkura WhatsApp link-a tap panni "Hi" nu send pannunga. Appuram naan anga assist panren.

$whatsappLink
                """.trimIndent()
            } else {
                """
Hi $cleanName, Imran unga call-a attend panna mudiyala. Naan Imran-oda AI Receptionist. Keezha irukkura WhatsApp link-a tap panni "Hi" nu send pannunga. Naan anga unga message-a Imran kaga handle panren.

$whatsappLink
                """.trimIndent()
            }

        return try {

            val smsManager =
                SmsManager.getSmsManagerForSubscriptionId(
                    sim1Subscription.subscriptionId
                )

            /*
             * Message is longer than one SMS.
             * divideMessage + sendMultipartTextMessage prevents
             * device/carrier truncation.
             */
            val parts =
                smsManager.divideMessage(message)

            smsManager.sendMultipartTextMessage(
                callerNumber,
                null,
                parts,
                null,
                null
            )

            DiagnosticLogger.log(
                context,
                "SMS_SENT",
                "Follow-up queued from SIM1; " +
                    "parts=${parts.size}; " +
                    if (cleanName == null)
                        "caller=UNKNOWN"
                    else
                        "caller=SAVED_CONTACT"
            )

            true

        } catch (e: Exception) {

            DiagnosticLogger.log(
                context,
                "SMS_ERROR",
                e.javaClass.simpleName +
                    ": " +
                    (e.message ?: "Unknown error")
            )

            false
        }
    }
}
