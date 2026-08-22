package com.imran.receptionist

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.imran.receptionist.databinding.ActivityMainBinding
import com.imran.receptionist.status.StatusViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val statusViewModel: StatusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupStatusButtons()
        observeStatus()
    }

    private fun setupStatusButtons() {
        binding.btnWork.setOnClickListener {
            lifecycleScope.launch {
                statusViewModel.setStatus("Work")
            }
        }

        binding.btnSleep.setOnClickListener {
            lifecycleScope.launch {
                statusViewModel.setStatus("Sleep")
            }
        }

        binding.btnOuting.setOnClickListener {
            lifecycleScope.launch {
                statusViewModel.setStatus("Outing")
            }
        }
    }

    private fun observeStatus() {
        lifecycleScope.launch {
            statusViewModel.currentStatus.collect { status ->
                updateStatusUI(status)
            }
        }
    }

    private fun updateStatusUI(status: String) {
        // Reset all buttons
        binding.btnWork.alpha = 0.5f
        binding.btnSleep.alpha = 0.5f
        binding.btnOuting.alpha = 0.5f

        // Highlight current status
        when (status) {
            "Work" -> binding.btnWork.alpha = 1.0f
            "Sleep" -> binding.btnSleep.alpha = 1.0f
            "Outing" -> binding.btnOuting.alpha = 1.0f
        }
    }
}
