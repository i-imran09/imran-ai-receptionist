package com.imran.receptionist.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val callerNumber: String,
    val status: String,
    val type: String, // "incoming_call", "whatsapp_message", etc.
    val eventId: String,
    val whatsappMessageId: String? = null,
    val timestamp: Date
)
