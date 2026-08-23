package com.imran.receptionist

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.imran.receptionist.databinding.ActivityMainBinding
import com.imran.receptionist.status.StatusRepository
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var statusRepository: StatusRepository

    private val contactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshReadyState() }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshReadyState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        statusRepository = StatusRepository(applicationContext)

        val statuses = listOf(
            "Work",
            "Sleep",
            "Outing",
            "Driving",
            "Meeting",
            "Eating",
            "Travel",
            "Exercise",
            "Personal Work",
            "Family Time",
            "Prayer",
            "Busy",
            "Free"
        )

        val statusAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            statuses
        )

        statusAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        binding.spinnerStatus.adapter = statusAdapter

        binding.spinnerStatus.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selected = statuses[position]

                    if (binding.tvCurrentStatus.text.toString() != selected) {
                        setStatus(selected)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

        binding.btnEnable.setOnClickListener { startSetup() }

        binding.btnConversations.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    com.imran.receptionist.conversations.ConversationsActivity::class.java
                )
            )
        }

        binding.btnDatabase.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    com.imran.receptionist.databaseui.DatabaseActivity::class.java
                )
            )
        }

        lifecycleScope.launch {
            statusRepository.currentStatus.collect {
                binding.tvCurrentStatus.text = it

                val position = statuses.indexOf(it)
                if (position >= 0 &&
                    binding.spinnerStatus.selectedItemPosition != position) {
                    binding.spinnerStatus.setSelection(position)
                }
            }
        }
    }

    private fun setStatus(value: String) =
        lifecycleScope.launch { statusRepository.setStatus(value) }

    private fun startSetup() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
            return
        }
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            return
        }
        refreshReadyState()
    }

    private fun refreshReadyState() {
        val contactOk = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val rm = getSystemService(RoleManager::class.java)
        val roleOk = rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        binding.tvSetup.text = if (contactOk && roleOk) "AI Receptionist Ready ✓"
        else "Setup required: tap Enable AI Receptionist"
    }

    override fun onResume() {
        super.onResume()
        refreshReadyState()
    }
}
