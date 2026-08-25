package com.imran.receptionist.reminder

import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.imran.receptionist.databinding.ActivityCallbackRescheduleBinding
import com.imran.receptionist.diagnostics.DiagnosticLogger
import com.imran.receptionist.network.ApiService
import com.imran.receptionist.network.CallbackDecisionRequest
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CallbackRescheduleActivity : AppCompatActivity() {

    private lateinit var binding:
        ActivityCallbackRescheduleBinding

    private var selectedTime:
        OffsetDateTime? = null

    private lateinit var phone: String
    private var callerName: String? = null
    private var callerReason: String? = null
    private var requestedTime: String? = null

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityCallbackRescheduleBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        phone =
            intent.getStringExtra(
                ReminderScheduler.EXTRA_PHONE
            ).orEmpty()

        callerName =
            intent.getStringExtra(
                ReminderScheduler.EXTRA_NAME
            )

        callerReason =
            intent.getStringExtra(
                ReminderScheduler.EXTRA_REASON
            )

        requestedTime =
            intent.getStringExtra(
                CallbackApprovalReceiver.EXTRA_REQUESTED_TIME
            )

        binding.tvCallerName.text =
            callerName
                ?.takeIf { it.isNotBlank() }
                ?: "Unknown caller"

        binding.tvPhone.text =
            phone

        binding.tvReason.text =
            if (!callerReason.isNullOrBlank()) {
                "Reason: $callerReason"
            } else {
                "Reason not available"
            }

        binding.tvRequestedTime.text =
            if (!requestedTime.isNullOrBlank()) {
                "Caller requested: " +
                    formatForDisplay(
                        requestedTime!!
                    )
            } else {
                "Caller requested time not available"
            }

        binding.btnChooseTime.setOnClickListener {
            openDatePicker()
        }

        binding.btnConfirmReschedule
            .setOnClickListener {

                confirmReschedule()
            }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun openDatePicker() {

        val now =
            OffsetDateTime.now(
                ZoneId.of(
                    "Asia/Kolkata"
                )
            )

        DatePickerDialog(
            this,
            { _, year, month, day ->

                openTimePicker(
                    year,
                    month + 1,
                    day
                )
            },
            now.year,
            now.monthValue - 1,
            now.dayOfMonth
        ).apply {

            datePicker.minDate =
                System.currentTimeMillis()

            show()
        }
    }

    private fun openTimePicker(
        year: Int,
        month: Int,
        day: Int
    ) {

        val now =
            OffsetDateTime.now(
                ZoneId.of(
                    "Asia/Kolkata"
                )
            )

        TimePickerDialog(
            this,
            { _, hour, minute ->

                val candidate =
                    OffsetDateTime.of(
                        year,
                        month,
                        day,
                        hour,
                        minute,
                        0,
                        0,
                        now.offset
                    )

                if (
                    candidate.toInstant()
                        .toEpochMilli() <=
                    System.currentTimeMillis()
                ) {

                    Toast.makeText(
                        this,
                        "Please select a future time",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@TimePickerDialog
                }

                selectedTime =
                    candidate

                binding.tvSelectedTime.text =
                    candidate.format(
                        DateTimeFormatter.ofPattern(
                            "dd MMM yyyy, hh:mm a"
                        )
                    )
            },
            now.hour,
            now.minute,
            false
        ).show()
    }

    private fun confirmReschedule() {

        val finalTime =
            selectedTime

        if (finalTime == null) {

            Toast.makeText(
                this,
                "Please choose a new date and time",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        binding.btnConfirmReschedule
            .isEnabled = false

        lifecycleScope.launch {

            try {

                val response =
                    ApiService.create()
                        .postCallbackDecision(
                            CallbackDecisionRequest(
                                phone_number =
                                    phone,
                                decision =
                                    "RESCHEDULE",
                                confirmed_callback_time =
                                    finalTime.toString()
                            )
                        )

                val body =
                    response.body()

                if (
                    response.isSuccessful &&
                    body?.success == true
                ) {

                    val confirmed =
                        body.confirmed_callback_time
                            ?: finalTime.toString()

                    val scheduled =
                        ReminderScheduler
                            .scheduleCallback(
                                context =
                                    applicationContext,
                                phoneNumber =
                                    phone,
                                callerName =
                                    callerName,
                                callerReason =
                                    callerReason,
                                callbackTimeIso =
                                    confirmed
                            )

                    dismissApprovalNotification()

                    DiagnosticLogger.log(
                        this@CallbackRescheduleActivity,
                        "CALLBACK_RESCHEDULED",
                        if (scheduled) {
                            "Callback rescheduled and reminder created"
                        } else {
                            "Callback rescheduled but reminder scheduling failed"
                        }
                    )

                    Toast.makeText(
                        this@CallbackRescheduleActivity,
                        "Callback rescheduled",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()

                } else {

                    binding.btnConfirmReschedule
                        .isEnabled = true

                    Toast.makeText(
                        this@CallbackRescheduleActivity,
                        "Unable to reschedule callback",
                        Toast.LENGTH_SHORT
                    ).show()

                    DiagnosticLogger.log(
                        this@CallbackRescheduleActivity,
                        "CALLBACK_APPROVAL_ERROR",
                        "Reschedule HTTP ${response.code()}"
                    )
                }

            } catch (e: Exception) {

                binding.btnConfirmReschedule
                    .isEnabled = true

                DiagnosticLogger.log(
                    this@CallbackRescheduleActivity,
                    "CALLBACK_APPROVAL_ERROR",
                    "Reschedule " +
                        e.javaClass.simpleName +
                        ": " +
                        (e.message ?: "Unknown")
                )

                Toast.makeText(
                    this@CallbackRescheduleActivity,
                    "Unable to reschedule callback",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun dismissApprovalNotification() {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.cancel(
            CallbackApprovalReceiver
                .notificationId(phone)
        )
    }

    private fun formatForDisplay(
        iso: String
    ): String {

        return try {

            OffsetDateTime
                .parse(iso)
                .format(
                    DateTimeFormatter.ofPattern(
                        "dd MMM yyyy, hh:mm a"
                    )
                )

        } catch (_: Exception) {
            iso
        }
    }
}
