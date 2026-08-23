package com.imran.receptionist.conversations

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.imran.receptionist.network.ApiService
import com.imran.receptionist.network.ConversationItem
import com.imran.receptionist.databinding.ActivityConversationsBinding
import kotlinx.coroutines.launch

class ConversationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationsBinding
    private val api by lazy { ApiService.create() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConversationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadConversations()
    }

    private fun loadConversations() {
        binding.progress.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = api.getConversations()

                binding.progress.visibility = View.GONE

                if (!response.isSuccessful) {
                    binding.tvEmpty.text = "Unable to load conversations"
                    binding.tvEmpty.visibility = View.VISIBLE
                    return@launch
                }

                val body = response.body()

                if (body == null || body.conversations.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    return@launch
                }

                binding.container.removeAllViews()

                body.conversations.forEach {
                    addConversationCard(it)
                }

            } catch (e: Exception) {
                binding.progress.visibility = View.GONE
                binding.tvEmpty.text = "Network error"
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun addConversationCard(item: ConversationItem) {

        val card = MaterialCardView(this).apply {
            radius = 18f
            cardElevation = 0f
            setContentPadding(18, 18, 18, 18)

            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 14
            }
        }

        val text = TextView(this).apply {
            val displayName = item.caller_name?.takeIf { it.isNotBlank() }
                ?: "Unknown Caller"

            val preview = item.last_message ?: "No messages"

            this.text = """
                $displayName
                ${item.phone_number}

                $preview

                ${item.message_count} messages
            """.trimIndent()

            textSize = 15f
            setTextColor(android.graphics.Color.parseColor("#111827"))
        }

        card.addView(text)

        card.setOnClickListener {
            val intent = android.content.Intent(
                this,
                ConversationDetailActivity::class.java
            )

            intent.putExtra("phone_number", item.phone_number)
            intent.putExtra("caller_name", item.caller_name ?: "Unknown Caller")

            startActivity(intent)
        }

        binding.container.addView(card)
    }
}
