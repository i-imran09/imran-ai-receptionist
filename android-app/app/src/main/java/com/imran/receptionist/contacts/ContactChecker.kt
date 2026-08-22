package com.imran.receptionist.contacts

import android.content.Context
import android.provider.ContactsContract

object ContactChecker {
    fun isSavedContact(context: Context, phoneNumber: String): Boolean {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?",
            arrayOf(phoneNumber),
            null
        )

        val isSaved = cursor?.let {
            val exists = it.count > 0
            it.close()
            exists
        } ?: false

        return isSaved
    }
}
