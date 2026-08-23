package com.imran.receptionist.status

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StatusViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StatusRepository(application)

    val currentStatus = repository.currentStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Work")

    fun setStatus(status: String) {
        viewModelScope.launch {
            repository.setStatus(status)
        }
    }
}
