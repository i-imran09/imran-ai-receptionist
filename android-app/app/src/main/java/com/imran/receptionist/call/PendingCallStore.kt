package com.imran.receptionist.call

import android.content.Context

data class PendingIncomingCall(
    val number: String,
    val phoneAccountId: String?,
    val startedAt: Long,
    val simSlot: Int
)

object PendingCallStore {

    private const val PREFS = "imran_pending_call"
    private const val NUMBER = "number"
    private const val ACCOUNT = "account"
    private const val STARTED = "started"
    private const val SIM = "sim"

    fun save(
        context: Context,
        call: PendingIncomingCall
    ) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .putString(NUMBER, call.number)
            .putString(ACCOUNT, call.phoneAccountId)
            .putLong(STARTED, call.startedAt)
            .putInt(SIM, call.simSlot)
            .apply()
    }

    fun get(context: Context): PendingIncomingCall? {
        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

        val number =
            prefs.getString(NUMBER, null)
                ?: return null

        return PendingIncomingCall(
            number = number,
            phoneAccountId =
                prefs.getString(ACCOUNT, null),
            startedAt =
                prefs.getLong(STARTED, 0L),
            simSlot =
                prefs.getInt(SIM, -1)
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit().clear().apply()
    }
}
