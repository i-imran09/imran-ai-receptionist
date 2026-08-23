package com.imran.receptionist

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
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

        binding.btnWork.setOnClickListener { setStatus("Work") }
        binding.btnSleep.setOnClickListener { setStatus("Sleep") }
        binding.btnOuting.setOnClickListener { setStatus("Outing") }
        binding.btnEnable.setOnClickListener { startSetup() }

        lifecycleScope.launch {
            statusRepository.currentStatus.collect {
                binding.tvCurrentStatus.text = it
                binding.btnWork.alpha = if (it == "Work") 1f else .5f
                binding.btnSleep.alpha = if (it == "Sleep") 1f else .5f
                binding.btnOuting.alpha = if (it == "Outing") 1f else .5f
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
