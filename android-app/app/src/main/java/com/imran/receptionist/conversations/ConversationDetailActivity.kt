package com.imran.receptionist.conversations

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.imran.receptionist.databinding.ActivityConversationDetailBinding
import com.imran.receptionist.network.ApiService
import kotlinx.coroutines.launch

class ConversationDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationDetailBinding
    private val api by lazy { ApiService.create() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConversationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val phone = intent.getStringExtra("phone_number") ?: return
        val name = intent.getStringExtra("caller_name") ?: "Unknown Caller"

        binding.tvTitle.text = name
        binding.tvPhone.text = phone

        loadMessages(phone)
    }

    private fun loadMessages(phone: String) {
        lifecycleScope.launch {
            try {
                val response = api.getConversations()
                val conversations = response.body()?.conversations ?: return@launch

                val item = conversations.firstOrNull {
                    it.phone_number == phone
                } ?: return@launch

                binding.messagesContainer.removeAllViews()

                item.messages.forEach { msg ->

                    val isAi = msg.role == "assistant"

                    val card = MaterialCardView(this@ConversationDetailActivity).apply {
                        radius = 16f
                        cardElevation = 0f

                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = if (isAi) Gravity.START else Gravity.END
                            bottomMargin = 12
                        }

                        setContentPadding(16, 12, 16, 12)
                    }

                    val text = TextView(this@ConversationDetailActivity).apply {
                        this.text = msg.message ?: ""
                        textSize = 15f
                    }

                    card.addView(text)
                    binding.messagesContainer.addView(card)
                }

            } catch (_: Exception) {
            }
        }
    }
}
