package com.imran.receptionist.diagnostics

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticLogger {

    private const val PREFS = "imran_ai_diagnostics"
    private const val KEY_LOGS = "logs"
    private const val MAX_ENTRIES = 200

    @Synchronized
    fun log(
        context: Context,
        stage: String,
        message: String
    ) {
        val time = SimpleDateFormat(
            "dd MMM yyyy, hh:mm:ss a",
            Locale.getDefault()
        ).format(Date())

        val entry = "[$time] [$stage] $message"

        Log.i("ImranAI", "$stage: $message")

        val prefs = context.applicationContext
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val current = prefs
            .getString(KEY_LOGS, "")
            .orEmpty()
            .lines()
            .filter { it.isNotBlank() }
            .toMutableList()

        current.add(entry)

        val trimmed =
            if (current.size > MAX_ENTRIES) {
                current.takeLast(MAX_ENTRIES)
            } else {
                current
            }

        prefs.edit()
            .putString(
                KEY_LOGS,
                trimmed.joinToString("\n")
            )
            .apply()
    }

    fun getLogs(context: Context): String {
        return context.applicationContext
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getString(KEY_LOGS, "")
            .orEmpty()
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(KEY_LOGS)
            .apply()
    }
}
