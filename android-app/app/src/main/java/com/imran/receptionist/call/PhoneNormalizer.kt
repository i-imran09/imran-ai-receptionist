package com.imran.receptionist.call

object PhoneNormalizer {
    fun normalize(phoneNumber: String): String {
        // Remove all non-digit characters
        val digits = phoneNumber.replace(Regex("\\D"), "")

        // If 10 digits, assume India (add country code)
        if (digits.length == 10) {
            return "91$digits"
        }

        // Return as-is if already in international format or unclear
        return digits
    }
}
