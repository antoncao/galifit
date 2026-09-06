package com.tfm.galifit

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.tfm.galifit.adapter.ChatMessageAdapter
import com.tfm.galifit.data.model.ChatMessage
import com.tfm.galifit.util.chat.ChatContextBuilder
import com.tfm.galifit.util.chat.GeminiChatSession
import kotlinx.coroutines.launch

class ChatAssistantActivity : AppCompatActivity() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etInput: TextInputEditText
    private lateinit var btnSend: MaterialButton
    private lateinit var loading: ProgressBar
    private lateinit var adapter: ChatMessageAdapter

    private var isSending = false
    private var sessionReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_assistant)

        val origin = intent.getStringExtra(EXTRA_CHAT_ORIGIN) ?: ORIGIN_HOME

        findViewById<MaterialToolbar>(R.id.chatToolbar).setNavigationOnClickListener {
            finish()
        }

        rvMessages = findViewById(R.id.rvChatMessages)
        etInput = findViewById(R.id.etChatInput)
        btnSend = findViewById(R.id.btnChatSend)
        loading = findViewById(R.id.chatLoading)

        adapter = ChatMessageAdapter()
        rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvMessages.adapter = adapter

        prepareSession(origin)

        btnSend.setOnClickListener { sendCurrentMessage() }
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentMessage()
                true
            } else {
                false
            }
        }
    }

    private fun showWelcomeMessage() {
        val welcome = ChatMessage(getString(R.string.chat_welcome), isUser = false)
        GeminiChatSession.addLocalMessage(welcome)
        adapter.submitList(listOf(welcome))
    }

    private fun prepareSession(origin: String) {
        setInputEnabled(false)
        loading.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = runCatching { ChatContextBuilder.build(origin) }
            loading.visibility = View.GONE

            result.onSuccess { build ->
                GeminiChatSession.ensureSession(build.systemInstruction, build.contextHash)
                val history = GeminiChatSession.getMessageHistory()
                if (history.isEmpty()) {
                    showWelcomeMessage()
                } else {
                    adapter.submitList(history)
                    scrollToBottom()
                }
                sessionReady = true
                setInputEnabled(true)
            }.onFailure {
                Toast.makeText(this@ChatAssistantActivity, R.string.chat_error, Toast.LENGTH_LONG).show()
                sessionReady = false
                setInputEnabled(false)
            }
        }
    }

    private fun sendCurrentMessage() {
        if (!sessionReady || isSending) return

        val text = etInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return

        etInput.text?.clear()
        isSending = true
        setInputEnabled(false)
        loading.visibility = View.VISIBLE

        val userMessage = ChatMessage(text, isUser = true)
        GeminiChatSession.addLocalMessage(userMessage)
        adapter.appendMessage(userMessage)
        scrollToBottom()

        lifecycleScope.launch {
            val responseResult = GeminiChatSession.sendMessage(text)
            loading.visibility = View.GONE
            isSending = false
            setInputEnabled(sessionReady)

            responseResult.onSuccess { reply ->
                val botMessage = ChatMessage(reply, isUser = false)
                GeminiChatSession.addLocalMessage(botMessage)
                adapter.appendMessage(botMessage)
                scrollToBottom()
            }.onFailure {
                Toast.makeText(this@ChatAssistantActivity, R.string.chat_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setInputEnabled(enabled: Boolean) {
        etInput.isEnabled = enabled
        btnSend.isEnabled = enabled
    }

    private fun scrollToBottom() {
        val count = adapter.itemCount
        if (count > 0) {
            rvMessages.post { rvMessages.smoothScrollToPosition(count - 1) }
        }
    }

    companion object {

        const val EXTRA_CHAT_ORIGIN = "chat_origin"

        const val ORIGIN_HOME = "home"

        const val ORIGIN_MEALS = "meals"

        const val ORIGIN_EXERCISES = "exercises"
    }
}
