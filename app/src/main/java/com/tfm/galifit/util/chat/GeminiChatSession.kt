package com.tfm.galifit.util.chat

import com.google.firebase.ai.Chat
import com.tfm.galifit.data.model.ChatMessage
import com.tfm.galifit.util.GalifitFlowLog
import com.tfm.galifit.util.ai.GeminiModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiChatSession {

    private var chat: Chat? = null
    private var cachedContextHash: Int? = null
    private val messageHistory = mutableListOf<ChatMessage>()

    fun getMessageHistory(): List<ChatMessage> = messageHistory.toList()

    fun hasActiveSession(): Boolean = chat != null

    fun ensureSession(systemInstruction: String, contextHash: Int) {
        if (chat != null && cachedContextHash == contextHash) return
        if (cachedContextHash != null && cachedContextHash != contextHash) {
            messageHistory.clear()
            GalifitFlowLog.api("Chat: historial limpiado por cambio de contexto")
        }
        val model = GeminiModelProvider.generativeModel(systemInstruction = systemInstruction)
        chat = model.startChat()
        cachedContextHash = contextHash
        GalifitFlowLog.api("Chat: sesión Gemini lista (hash=$contextHash)")
    }

    fun addLocalMessage(message: ChatMessage) {
        messageHistory.add(message)
    }

    suspend fun sendMessage(userText: String): Result<String> = withContext(Dispatchers.IO) {
        val activeChat = chat
            ?: return@withContext Result.failure(IllegalStateException("Sesión de chat no inicializada"))

        runCatching {
            val response = activeChat.sendMessage(userText)
            val text = response.text?.trim().orEmpty()
            if (text.isBlank()) {
                throw IllegalStateException("Respuesta vacía del modelo")
            }
            text
        }.onFailure {
            GalifitFlowLog.warn(
                "Chat: error al enviar mensaje a Gemini → ${it.javaClass.name}: ${it.message}"
            )
        }
    }

    fun clear() {
        chat = null
        cachedContextHash = null
        messageHistory.clear()
        GalifitFlowLog.api("Chat: sesión limpiada")
    }
}
