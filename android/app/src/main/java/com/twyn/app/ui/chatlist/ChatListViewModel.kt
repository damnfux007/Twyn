package com.twyn.app.ui.chatlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twyn.app.data.repository.ChatRepository
import com.twyn.app.data.repository.PairingRepository
import com.twyn.app.data.remote.websocket.TwynWebSocketClient
import com.twyn.app.domain.model.ChatMessage
import com.twyn.app.domain.model.ContentType
import com.twyn.app.domain.model.Pairing
import com.twyn.app.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import javax.inject.Inject

/**
 * ViewModel for the Chat List (home) screen.
 * Shows all active 1-on-1 paired chats.
 * Also manages the WebSocket connection lifecycle and incoming message routing.
 */
@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
    private val chatRepository: ChatRepository,
    private val webSocketClient: TwynWebSocketClient,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val pairings: StateFlow<List<Pairing>> = pairingRepository.getAllPairings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        connectWebSocket()
        handleIncomingMessages()
    }

    private fun connectWebSocket() {
        val userId = preferencesManager.userId
        Log.i("ChatListVM", "Connecting WebSocket for user: $userId")
        webSocketClient.connect(userId)
    }

    private fun handleIncomingMessages() {
        viewModelScope.launch {
            webSocketClient.incomingMessages.collect { message ->
                try {
                    when (message.type) {
                        "NEW_MESSAGE" -> {
                            val payload = Json.parseToJsonElement(message.payload) as? JsonObject ?: return@collect
                            val chatMessage = ChatMessage(
                                messageId = payload["messageId"]?.jsonPrimitive?.content ?: return@collect,
                                pairingId = payload["pairingId"]?.jsonPrimitive?.content ?: return@collect,
                                senderId = payload["senderId"]?.jsonPrimitive?.content ?: return@collect,
                                ciphertext = payload["ciphertext"]?.jsonPrimitive?.content ?: "",
                                contentType = ContentType.valueOf(
                                    payload["contentType"]?.jsonPrimitive?.content ?: "TEXT"
                                ),
                                timestamp = payload["timestamp"]?.jsonPrimitive?.long
                                    ?: System.currentTimeMillis(),
                                isFromMe = false,
                                isDelivered = true,
                                decryptedText = payload["plaintext"]?.jsonPrimitive?.content
                                    ?: payload["ciphertext"]?.jsonPrimitive?.content
                            )
                            chatRepository.receiveMessage(chatMessage)
                            Log.i("ChatListVM", "Stored incoming message: ${chatMessage.messageId}")
                        }
                        "PARTNER_PROFILE" -> {
                            val payload = Json.parseToJsonElement(message.payload) as? JsonObject ?: return@collect
                            val partnerId = payload["partnerId"]?.jsonPrimitive?.content ?: return@collect
                            val partnerName = payload["displayName"]?.jsonPrimitive?.content ?: return@collect
                            pairingRepository.updatePartnerName(partnerId, partnerName)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatListVM", "Error handling incoming message: ${e.message}", e)
                }
            }
        }
    }
}
