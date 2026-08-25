package com.imran.receptionist.reminder

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.imran.receptionist.databinding.ActivityCancelReasonBinding
import com.imran.receptionist.diagnostics.DiagnosticLogger

class CancelReasonActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCancelReasonBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityCancelReasonBinding.inflate(layoutInflater)

        setContentView(binding.root)

        val phone =
            intent.getStringExtra(
                ReminderScheduler.EXTRA_PHONE
            ).orEmpty()

        val name =
            intent.getStringExtra(
                ReminderScheduler.EXTRA_NAME
            )
                ?.takeIf { it.isNotBlank() }

        val originalReason =
            intent.getStringExtra(
                ReminderScheduler.EXTRA_REASON
            )
                ?.takeIf { it.isNotBlank() }

        binding.tvCallerName.text =
            name ?: "Unknown caller"

        binding.tvPhone.text =
            phone

        binding.tvReason.text =
            if (originalReason != null) {
                "Reason: $originalReason"
            } else {
                "Reason not available"
            }

        binding.radioGroup.setOnCheckedChangeListener {
                _,
                checkedId ->

            binding.otherReasonLayout.visibility =
                if (
                    checkedId ==
                    binding.radioOther.id
                ) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnConfirmCancel.setOnClickListener {

            val selectedReason =
                when (
                    binding.radioGroup
                        .checkedRadioButtonId
                ) {

                    binding.radioContacted.id ->
                        "Already contacted"

                    binding.radioNotRequired.id ->
                        "Not required"

                    binding.radioCallLater.id ->
                        "Call later"

                    binding.radioOther.id ->
                        binding.editOtherReason
                            .text
                            ?.toString()
                            ?.trim()
                            .orEmpty()

                    else -> ""
                }

            if (selectedReason.isBlank()) {

                Toast.makeText(
                    this,
                    "Please select or enter a reason",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            DiagnosticLogger.log(
                this,
                "REMINDER_CANCEL_REASON",
                "Caller=$phone; reason=$selectedReason"
            )

            Toast.makeText(
                this,
                "Reminder cancelled",
                Toast.LENGTH_SHORT
            ).show()

            finish()
        }
    }
}
