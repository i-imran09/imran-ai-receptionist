package com.imran.receptionist.diagnostics

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.imran.receptionist.databinding.ActivityDiagnosticsBinding

class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiagnosticsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityDiagnosticsBinding.inflate(layoutInflater)

        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnRefresh.setOnClickListener {
            refreshLogs()
        }

        binding.btnClear.setOnClickListener {
            DiagnosticLogger.clear(this)
            refreshLogs()
        }

        refreshLogs()
    }

    override fun onResume() {
        super.onResume()
        refreshLogs()
    }

    private fun refreshLogs() {
        val logs =
            DiagnosticLogger.getLogs(this)

        binding.tvLogs.text =
            if (logs.isBlank()) {
                "No diagnostics yet"
            } else {
                logs
            }

        binding.scrollLogs.post {
            binding.scrollLogs.fullScroll(
                android.view.View.FOCUS_DOWN
            )
        }
    }
}
