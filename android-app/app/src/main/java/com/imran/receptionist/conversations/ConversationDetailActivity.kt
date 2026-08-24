package com.imran.receptionist.conversations

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.imran.receptionist.databinding.ActivityConversationDetailBinding
import com.imran.receptionist.network.ApiService
import com.imran.receptionist.network.ConversationMessage
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class ConversationDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationDetailBinding
    private val api by lazy { ApiService.create() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityConversationDetailBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        val phone =
            intent.getStringExtra("phone_number")
                ?: run {
                    finish()
                    return
                }

        val name =
            intent.getStringExtra("caller_name")
                ?: "Unknown Caller"

        binding.tvTitle.text = name
        binding.tvPhone.text = phone

        binding.btnBack.setOnClickListener {
            finish()
        }

        loadMessages(phone)
    }

    private fun loadMessages(phone: String) {

        binding.progress.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
        binding.messagesContainer.removeAllViews()

        lifecycleScope.launch {
            try {
                val response =
                    api.getConversation(phone)

                if (!response.isSuccessful) {
                    showError(
                        "Unable to load chat history"
                    )
                    return@launch
                }

                val body = response.body()

                if (
                    body == null ||
                    body.messages.isEmpty()
                ) {
                    showError(
                        "No chat history for this caller"
                    )
                    return@launch
                }

                body.messages.forEach { msg ->
                    addMessageBubble(msg)
                }

                binding.chatScroll.post {
                    binding.chatScroll.fullScroll(
                        View.FOCUS_DOWN
                    )
                }

            } catch (_: Exception) {
                showError(
                    "Connection error while loading chat"
                )
            } finally {
                binding.progress.visibility = View.GONE
            }
        }
    }

    private fun addMessageBubble(
        msg: ConversationMessage
    ) {

        val cleaned =
            cleanMessage(msg.message)

        if (cleaned.isBlank()) {
            return
        }

        val isCaller =
            msg.role == "user"

        val card =
            MaterialCardView(this).apply {

                radius = dp(16).toFloat()
                cardElevation = 0f

                setCardBackgroundColor(
                    if (isCaller) {
                        Color.parseColor("#EDE9FE")
                    } else {
                        Color.WHITE
                    }
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {

                        width =
                            (resources.displayMetrics.widthPixels * 0.78)
                                .toInt()

                        gravity =
                            if (isCaller) {
                                Gravity.END
                            } else {
                                Gravity.START
                            }

                        bottomMargin = dp(10)
                    }

                setContentPadding(
                    dp(14),
                    dp(11),
                    dp(14),
                    dp(9)
                )
            }

        val wrapper =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
            }

        val messageText =
            TextView(this).apply {
                text = cleaned
                textSize = 15f
                setTextColor(
                    Color.parseColor("#111827")
                )
            }

        val meta =
            TextView(this).apply {

                val sender =
                    if (isCaller) {
                        "Caller"
                    } else {
                        "AI Receptionist"
                    }

                val time =
                    formatTime(msg.created_at)

                text =
                    if (time.isBlank()) {
                        sender
                    } else {
                        "$sender • $time"
                    }

                textSize = 10f

                setTextColor(
                    Color.parseColor("#6B7280")
                )

                gravity = Gravity.END

                setPadding(
                    0,
                    dp(6),
                    0,
                    0
                )
            }

        wrapper.addView(messageText)
        wrapper.addView(meta)

        card.addView(wrapper)

        binding.messagesContainer.addView(card)
    }

    private fun cleanMessage(
        message: String?
    ): String {

        if (message.isNullOrBlank()) {
            return ""
        }

        if (
            message.trim()
                .startsWith(
                    "<think>",
                    ignoreCase = true
                )
        ) {
            return ""
        }

        return message.replace(
            Regex(
                "<think>.*?</think>",
                setOf(
                    RegexOption.IGNORE_CASE,
                    RegexOption.DOT_MATCHES_ALL
                )
            ),
            ""
        ).trim()
    }

    private fun formatTime(
        value: String?
    ): String {

        if (value.isNullOrBlank()) {
            return ""
        }

        return try {
            val time =
                OffsetDateTime.parse(value)

            time.format(
                DateTimeFormatter.ofPattern(
                    "dd MMM, hh:mm a"
                )
            )

        } catch (_: Exception) {
            ""
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility =
            View.VISIBLE
    }

    private fun dp(value: Int): Int =
        (value *
            resources.displayMetrics.density)
            .toInt()
}
