package com.imran.receptionist.contacts

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactChecker(
    private val context: Context
) {

    suspend fun getContactName(
        number: String
    ): String? = withContext(Dispatchers.IO) {

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )

        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->

            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(
                    ContactsContract.PhoneLookup.DISPLAY_NAME
                )

                if (index >= 0) {
                    cursor.getString(index)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                } else {
                    null
                }

            } else {
                null
            }

        }
    }

    suspend fun isContactSaved(
        number: String
    ): Boolean {
        return getContactName(number) != null
    }
}
