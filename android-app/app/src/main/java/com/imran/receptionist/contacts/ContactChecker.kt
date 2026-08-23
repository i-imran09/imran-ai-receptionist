package com.imran.receptionist.contacts

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactChecker(private val context: Context) {
    suspend fun isContactSaved(number: String): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup._ID),
            null, null, null
        )?.use { it.moveToFirst() } ?: false
    }
}
