package com.imran.receptionist.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.imran.receptionist.MainActivity
import com.imran.receptionist.R
import com.imran.receptionist.diagnostics.DiagnosticLogger

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        val phone =
            intent.getStringExtra(
                ReminderScheduler.EXTRA_PHONE
            ) ?: return

        val name =
            intent.getStringExtra(
                ReminderScheduler.EXTRA_NAME
            )
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        val reason =
            intent.getStringExtra(
                ReminderScheduler.EXTRA_REASON
            )
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        val emergency =
            intent.getBooleanExtra(
                ReminderScheduler.EXTRA_EMERGENCY,
                false
            )

        createNotificationChannel(
            context,
            emergency
        )

        val displayName =
            name ?: phone

        val title =
            if (emergency) {
                "🚨 Emergency — $displayName"
            } else {
                "📞 Call $displayName"
            }

        val body =
            if (reason != null) {
                reason
            } else if (emergency) {
                "Emergency attention required"
            } else {
                "Scheduled callback reminder"
            }

        // -----------------------------------------
        // Tap notification -> open app
        // -----------------------------------------

        val openAppIntent =
            Intent(
                context,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val openAppPending =
            PendingIntent.getActivity(
                context,
                phone.hashCode() + 10,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        // -----------------------------------------
        // CALL NOW -> open phone dialer
        // -----------------------------------------

        val dialIntent =
            Intent(
                Intent.ACTION_DIAL,
                Uri.parse("tel:$phone")
            )

        val dialPending =
            PendingIntent.getActivity(
                context,
                phone.hashCode() + 20,
                dialIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        // -----------------------------------------
        // SNOOZE action
        // -----------------------------------------

        val snoozeIntent =
            Intent(
                context,
                ReminderActionReceiver::class.java
            ).apply {

                action =
                    ReminderActionReceiver.ACTION_SNOOZE

                putExtra(
                    ReminderScheduler.EXTRA_PHONE,
                    phone
                )

                putExtra(
                    ReminderScheduler.EXTRA_NAME,
                    name
                )

                putExtra(
                    ReminderScheduler.EXTRA_REASON,
                    reason
                )
            }

        val snoozePending =
            PendingIntent.getBroadcast(
                context,
                phone.hashCode() + 30,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        // -----------------------------------------
        // CANCEL action
        // -----------------------------------------

        val cancelIntent =
            Intent(
                context,
                ReminderActionReceiver::class.java
            ).apply {

                action =
                    ReminderActionReceiver.ACTION_CANCEL

                putExtra(
                    ReminderScheduler.EXTRA_PHONE,
                    phone
                )

                putExtra(
                    ReminderScheduler.EXTRA_NAME,
                    name
                )

                putExtra(
                    ReminderScheduler.EXTRA_REASON,
                    reason
                )
            }

        val cancelPending =
            PendingIntent.getBroadcast(
                context,
                phone.hashCode() + 40,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val channelId =
            if (emergency) {
                EMERGENCY_CHANNEL
            } else {
                CALLBACK_CHANNEL
            }

        val notification =
            NotificationCompat.Builder(
                context,
                channelId
            )
                .setSmallIcon(
                    R.mipmap.ic_launcher
                )
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(body)
                )
                .setContentIntent(
                    openAppPending
                )
                .setAutoCancel(false)
                .setOngoing(emergency)
                .setPriority(
                    if (emergency) {
                        NotificationCompat
                            .PRIORITY_MAX
                    } else {
                        NotificationCompat
                            .PRIORITY_HIGH
                    }
                )
                .setCategory(
                    if (emergency) {
                        NotificationCompat
                            .CATEGORY_ALARM
                    } else {
                        NotificationCompat
                            .CATEGORY_REMINDER
                    }
                )
                .addAction(
                    0,
                    "CALL NOW",
                    dialPending
                )
                .addAction(
                    0,
                    "SNOOZE",
                    snoozePending
                )
                .addAction(
                    0,
                    "CANCEL",
                    cancelPending
                )
                .build()

        val manager =
            context.getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            notificationId(phone, emergency),
            notification
        )

        DiagnosticLogger.log(
            context,
            if (emergency)
                "EMERGENCY_ALERT"
            else
                "REMINDER_ALERT",
            "Notification shown for $displayName"
        )
    }

    private fun createNotificationChannel(
        context: Context,
        emergency: Boolean
    ) {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val manager =
            context.getSystemService(
                NotificationManager::class.java
            )

        val channelId =
            if (emergency) {
                EMERGENCY_CHANNEL
            } else {
                CALLBACK_CHANNEL
            }

        val channelName =
            if (emergency) {
                "Emergency Alerts"
            } else {
                "Callback Reminders"
            }

        val importance =
            NotificationManager
                .IMPORTANCE_HIGH

        val channel =
            NotificationChannel(
                channelId,
                channelName,
                importance
            )

        channel.description =
            if (emergency) {
                "Urgent caller alerts"
            } else {
                "Scheduled callback reminders"
            }

        val sound =
            RingtoneManager.getDefaultUri(
                if (emergency) {
                    RingtoneManager.TYPE_ALARM
                } else {
                    RingtoneManager.TYPE_NOTIFICATION
                }
            )

        val audioAttributes =
            AudioAttributes.Builder()
                .setUsage(
                    if (emergency) {
                        AudioAttributes
                            .USAGE_ALARM
                    } else {
                        AudioAttributes
                            .USAGE_NOTIFICATION
                    }
                )
                .build()

        channel.setSound(
            sound,
            audioAttributes
        )

        channel.enableVibration(true)

        manager.createNotificationChannel(
            channel
        )
    }

    companion object {

        const val CALLBACK_CHANNEL =
            "callback_reminders"

        const val EMERGENCY_CHANNEL =
            "emergency_alerts"

        fun notificationId(
            phone: String,
            emergency: Boolean
        ): Int {
            return (
                phone +
                    if (emergency)
                        "_emergency"
                    else
                        "_callback"
                ).hashCode()
        }
    }
}
