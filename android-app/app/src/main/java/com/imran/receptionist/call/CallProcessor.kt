package com.imran.receptionist.call

import android.content.Context
import android.util.Log
import com.imran.receptionist.contacts.ContactChecker
import com.imran.receptionist.network.ApiService
import com.imran.receptionist.status.StatusRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object CallProcessor {
    private val debouncer = Debouncer<String>()

    fun processCall(context: Context, callerNumber: String) {
        // Debounce to prevent duplicate processing
        debouncer.debounce(callerNumber, 2000) {
            GlobalScope.launch {
                try {
                    val normalizedNumber = PhoneNormalizer.normalize(callerNumber)
                    Log.d("CallProcessor", "Processing call from: $normalizedNumber")

                    // Check if contact is saved
                    val isSavedContact = ContactChecker.isSavedContact(context, normalizedNumber)
                    if (isSavedContact) {
                        Log.d("CallProcessor", "Saved contact - ignoring")
                        return@launch
                    }

                    // Get current status
                    val statusRepository = StatusRepository(context)
                    val currentStatus = statusRepository.getStatus()

                    // Send to backend
                    val apiService = ApiService.create(context)
                    val response = apiService.reportCall(
                        callerNumber = normalizedNumber,
                        currentStatus = currentStatus
                    )

                    if (response.isSuccessful) {
                        Log.d("CallProcessor", "Call reported to backend")
                    } else {
                        Log.e("CallProcessor", "Failed to report call: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e("CallProcessor", "Error processing call", e)
                }
            }
        }
    }
}
