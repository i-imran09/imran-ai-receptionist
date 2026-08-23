package com.imran.receptionist.databaseui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.imran.receptionist.databinding.ActivityDatabaseBinding
import com.imran.receptionist.network.ApiService
import com.imran.receptionist.network.ConversationItem
import kotlinx.coroutines.launch

class DatabaseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDatabaseBinding
    private val api by lazy { ApiService.create() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDatabaseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnRefresh.setOnClickListener {
            loadDatabase()
        }

        binding.btnClearConversations.setOnClickListener {
            confirmClearAll()
        }

        loadDatabase()
    }

    private fun loadDatabase() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        binding.callersContainer.removeAllViews()

        lifecycleScope.launch {
            try {
                val statsResponse = api.getDatabaseStats()
                val conversationsResponse = api.getConversations()

                if (!statsResponse.isSuccessful ||
                    !conversationsResponse.isSuccessful) {

                    showError("Unable to load database.")
                    return@launch
                }

                val stats = statsResponse.body()
                val conversations = conversationsResponse.body()

                if (stats == null || conversations == null) {
                    showError("Database returned an empty response.")
                    return@launch
                }

                binding.tvStorage.text =
                    if (stats.approx_data_mb >= 1.0)
                        String.format("%.2f MB", stats.approx_data_mb)
                    else
                        String.format("%.2f KB", stats.approx_data_kb)

                binding.tvCallerCount.text =
                    stats.caller_profiles.toString()

                binding.tvMessageCount.text =
                    stats.conversation_messages.toString()

                binding.tvDatabaseSummary.text =
                    "${conversations.caller_count} active conversation(s)"

                if (conversations.conversations.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    binding.tvEmpty.visibility = View.GONE

                    conversations.conversations.forEach {
                        addCallerCard(it)
                    }
                }

            } catch (e: Exception) {
                showError(
                    "Connection error. Please check internet and try again."
                )
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun addCallerCard(item: ConversationItem) {

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(16),
                dp(14),
                dp(16),
                dp(14)
            )

            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0xFFE5E7EB.toInt())
            }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        val displayName =
            item.caller_name
                ?.takeIf { it.isNotBlank() }
                ?: "Unknown caller"

        val name = TextView(this).apply {
            text = displayName
            textSize = 17f
            setTextColor(0xFF111827.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val phone = TextView(this).apply {
            text = item.phone_number
            textSize = 14f
            setTextColor(0xFF6B7280.toInt())
            setPadding(0, dp(3), 0, 0)
        }

        val messages = TextView(this).apply {
            text = "${item.message_count} stored message(s)"
            textSize = 13f
            setTextColor(0xFF374151.toInt())
            setPadding(0, dp(10), 0, 0)
        }

        val preview = TextView(this).apply {
            text = item.last_message
                ?.takeIf { it.isNotBlank() }
                ?: "No message preview"

            textSize = 14f
            maxLines = 2
            setTextColor(0xFF6B7280.toInt())
            setPadding(0, dp(5), 0, dp(12))
        }

        val deleteConversation =
            com.google.android.material.button.MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {

                text = "Delete Conversation"

                setOnClickListener {
                    confirmDeleteConversation(item)
                }
            }

        val deleteCaller =
            com.google.android.material.button.MaterialButton(
                this
            ).apply {

                text = "Delete Caller Data"

                setOnClickListener {
                    confirmDeleteCaller(item)
                }
            }

        card.addView(name)
        card.addView(phone)
        card.addView(messages)
        card.addView(preview)
        card.addView(deleteConversation)
        card.addView(deleteCaller)

        binding.callersContainer.addView(card)
    }

    private fun confirmDeleteConversation(
        item: ConversationItem
    ) {
        AlertDialog.Builder(this)
            .setTitle("Delete conversation?")
            .setMessage(
                "All stored conversation messages for " +
                    item.phone_number +
                    " will be permanently deleted.\n\n" +
                    "The saved caller profile will remain."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleteConversation(item.phone_number)
            }
            .show()
    }

    private fun confirmDeleteCaller(
        item: ConversationItem
    ) {
        val name =
            item.caller_name
                ?.takeIf { it.isNotBlank() }
                ?: item.phone_number

        AlertDialog.Builder(this)
            .setTitle("Delete caller data?")
            .setMessage(
                "This will permanently delete $name and " +
                    "all stored conversation history for this caller."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleteCaller(item.phone_number)
            }
            .show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle("Clear all conversations?")
            .setMessage(
                "All conversation messages stored in the database " +
                    "will be permanently deleted.\n\n" +
                    "Saved caller profiles will NOT be deleted."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear All") { _, _ ->
                clearAllConversations()
            }
            .show()
    }

    private fun deleteConversation(phone: String) {
        lifecycleScope.launch {
            try {
                val response = api.deleteConversation(phone)

                if (response.isSuccessful &&
                    response.body()?.success == true) {

                    Toast.makeText(
                        this@DatabaseActivity,
                        "Conversation deleted",
                        Toast.LENGTH_SHORT
                    ).show()

                    loadDatabase()
                } else {
                    showDeleteFailure()
                }

            } catch (e: Exception) {
                showDeleteFailure()
            }
        }
    }

    private fun deleteCaller(phone: String) {
        lifecycleScope.launch {
            try {
                val response = api.deleteCaller(phone)

                if (response.isSuccessful &&
                    response.body()?.success == true) {

                    Toast.makeText(
                        this@DatabaseActivity,
                        "Caller data deleted",
                        Toast.LENGTH_SHORT
                    ).show()

                    loadDatabase()
                } else {
                    showDeleteFailure()
                }

            } catch (e: Exception) {
                showDeleteFailure()
            }
        }
    }

    private fun clearAllConversations() {
        lifecycleScope.launch {
            try {
                val response =
                    api.clearAllConversations()

                if (response.isSuccessful &&
                    response.body()?.success == true) {

                    Toast.makeText(
                        this@DatabaseActivity,
                        "All conversations cleared",
                        Toast.LENGTH_SHORT
                    ).show()

                    loadDatabase()
                } else {
                    showDeleteFailure()
                }

            } catch (e: Exception) {
                showDeleteFailure()
            }
        }
    }

    private fun showDeleteFailure() {
        Toast.makeText(
            this,
            "Delete failed. Please try again.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
