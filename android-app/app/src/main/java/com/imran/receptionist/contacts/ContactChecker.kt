package com.imran.receptionist.contacts

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactChecker(private val context: Context) {

    suspend fun isContactSaved(phoneNumber: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val normalized = normalizePhoneNumber(phoneNumber)
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(normalized)
                .build()

            val cursor: Cursor? = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )

            cursor?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getContactName(phoneNumber: String): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val normalized = normalizePhoneNumber(phoneNumber)
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(normalized)
                .build()

            val cursor: Cursor? = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )

            var name: String? = null
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    name = it.getString(nameIndex)
                }
            }
            name
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizePhoneNumber(number: String): String {
        return number.replace(Regex("\\D"), "")
    }
}
