package com.imran.receptionist.status

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StatusViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StatusRepository(application)
    private val _currentStatus = MutableStateFlow<String>("Work")
    val currentStatus: StateFlow<String> = _currentStatus

    init {
        loadStatus()
    }

    private fun loadStatus() {
        viewModelScope.launch {
            _currentStatus.value = repository.getStatus()
        }
    }

    fun setStatus(status: String) {
        viewModelScope.launch {
            repository.setStatus(status)
            _currentStatus.value = status
        }
    }
}
