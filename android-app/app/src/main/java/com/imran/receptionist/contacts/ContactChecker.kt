package com.imran.receptionist.contacts

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract

class ContactChecker(private val context: Context) {

    fun isContactSaved(phoneNumber: String): Boolean {
        return try {
            val normalizedNumber = normalizePhoneNumber(phoneNumber)
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(normalizedNumber)
                .build()

            val cursor: Cursor? = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )

            cursor?.use {
                return it.count > 0
            }

            false
        } catch (e: Exception) {
            false
        }
    }

    fun getContactName(phoneNumber: String): String? {
        return try {
            val normalizedNumber = normalizePhoneNumber(phoneNumber)
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(normalizedNumber)
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
        // Remove all non-digit characters
        return number.replace(Regex("\\D"), "")
    }
}
