package com.imran.receptionist

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.imran.receptionist.databinding.ActivityMainBinding
import com.imran.receptionist.status.StatusManager
import com.imran.receptionist.history.HistoryManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var statusManager: StatusManager
    private lateinit var historyManager: HistoryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        statusManager = StatusManager(this)
        historyManager = HistoryManager(this)

        setupUI()
        observeStatus()
        loadHistory()
    }

    private fun setupUI() {
        binding.buttonWork.setOnClickListener {
            setStatus("Work")
        }
        binding.buttonSleep.setOnClickListener {
            setStatus("Sleep")
        }
        binding.buttonOuting.setOnClickListener {
            setStatus("Outing")
        }
    }

    private fun setStatus(status: String) {
        lifecycleScope.launch {
            try {
                statusManager.setStatus(status)
                updateStatusDisplay(status)
            } catch (e: Exception) {
                showError("Error setting status: ${e.message}")
            }
        }
    }

    private fun observeStatus() {
        lifecycleScope.launch {
            statusManager.currentStatus.collect { status ->
                if (status != null) {
                    updateStatusDisplay(status)
                }
            }
        }
    }

    private fun updateStatusDisplay(status: String) {
        binding.textCurrentStatus.text = "Current: $status"
        val color = when (status) {
            "Work" -> getColor(R.color.purple_500)
            "Sleep" -> getColor(R.color.teal_200)
            "Outing" -> getColor(R.color.orange_500)
            else -> getColor(R.color.black)
        }
        binding.textCurrentStatus.setTextColor(color)
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                val history = historyManager.getRecentHistory(limit = 10)
                if (history.isNotEmpty()) {
                    val historyText = history.joinToString("\n") { item ->
                        "${item.callerNumber} - ${item.timestamp} (${item.currentStatus})"
                    }
                    binding.textHistory.text = historyText
                } else {
                    binding.textHistory.text = getString(R.string.no_history)
                }
            } catch (e: Exception) {
                binding.textHistory.text = "Error loading history: ${e.message}"
            }
        }
    }

    private fun showError(message: String) {
        binding.textHistory.text = message
    }
}
