package com.imran.receptionist.conversations

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.imran.receptionist.databinding.ActivityConversationsBinding
import com.imran.receptionist.network.ApiService
import com.imran.receptionist.network.ConversationItem
import kotlinx.coroutines.launch

class ConversationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationsBinding
    private val api by lazy { ApiService.create() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConversationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        loadConversations()
    }

    override fun onResume() {
        super.onResume()

        if (::binding.isInitialized) {
            loadConversations()
        }
    }

    private fun loadConversations() {
        binding.progress.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        binding.container.removeAllViews()

        lifecycleScope.launch {
            try {
                val response = api.getConversations()

                if (!response.isSuccessful) {
                    showError("Unable to load conversations")
                    return@launch
                }

                val conversations =
                    response.body()?.conversations
                        ?.filter { it.message_count > 0 }
                        ?: emptyList()

                if (conversations.isEmpty()) {
                    binding.tvEmpty.text = "No conversations yet"
                    binding.tvEmpty.visibility = View.VISIBLE
                    return@launch
                }

                conversations.forEach {
                    addConversationCard(it)
                }

            } catch (_: Exception) {
                showError("Connection error")
            } finally {
                binding.progress.visibility = View.GONE
            }
        }
    }

    private fun addConversationCard(item: ConversationItem) {

        val card = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = Color.parseColor("#E5E7EB")
            isClickable = true
            isFocusable = true

            setCardBackgroundColor(Color.WHITE)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }

            setContentPadding(
                dp(16),
                dp(14),
                dp(16),
                dp(14)
            )
        }

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val displayName =
            item.caller_name
                ?.takeIf { it.isNotBlank() }
                ?: "Unknown Caller"

        val title = TextView(this).apply {
            text = displayName
            textSize = 17f
            setTextColor(Color.parseColor("#111827"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val phone = TextView(this).apply {
            text = item.phone_number
            textSize = 13f
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(2), 0, 0)
        }

        val preview = TextView(this).apply {
            text = cleanPreview(item.last_message)
            textSize = 14f
            setTextColor(Color.parseColor("#4B5563"))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(10), 0, 0)
        }

        val count = TextView(this).apply {
            text = "${item.message_count} messages"
            textSize = 12f
            setTextColor(Color.parseColor("#7C3AED"))
            setPadding(0, dp(9), 0, 0)
        }

        wrapper.addView(title)
        wrapper.addView(phone)
        wrapper.addView(preview)
        wrapper.addView(count)

        card.addView(wrapper)

        card.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    ConversationDetailActivity::class.java
                ).apply {
                    putExtra(
                        "phone_number",
                        item.phone_number
                    )

                    putExtra(
                        "caller_name",
                        displayName
                    )
                }
            )
        }

        binding.container.addView(card)
    }

    private fun cleanPreview(message: String?): String {
        if (message.isNullOrBlank()) {
            return "No message preview"
        }

        var cleaned = message

        cleaned = cleaned.replace(
            Regex(
                "<think>.*?</think>",
                setOf(
                    RegexOption.IGNORE_CASE,
                    RegexOption.DOT_MATCHES_ALL
                )
            ),
            ""
        )

        if (
            cleaned.trim()
                .startsWith("<think>", ignoreCase = true)
        ) {
            return "Previous AI test message"
        }

        return cleaned
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() }
            ?: "Previous AI test message"
    }

    private fun showError(message: String) {
        binding.tvEmpty.text = message
        binding.tvEmpty.visibility = View.VISIBLE
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
