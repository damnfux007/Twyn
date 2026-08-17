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
                        "INCOMING_MESSAGE" -> {
                            val payload = Json.parseToJsonElement(message.payload) as? JsonObject ?: return@collect
                            val chatMessage = ChatMessage(
                                messageId = payload["messageId"]?.jsonPrimitive?.content ?: return@collect,
                                pairingId = payload["pairingId"]?.jsonPrimitive?.content ?: return@collect,
                                senderId = payload["senderId"]?.jsonPrimitive?.content ?: return@collect,
                                ciphertext = payload["ciphertext"]?.jsonPrimitive?.content ?: "",
                                contentType = ContentType.valueOf(
                                    payload["contentType"]?.jsonPrimitive?.content ?: "TEXT"
                                ),
                                timestamp = System.currentTimeMillis(),
                                isFromMe = false,
                                isDelivered = true,
                                decryptedText = payload["ciphertext"]?.jsonPrimitive?.content
                            )
                            chatRepository.receiveMessage(chatMessage)
                            Log.i("ChatListVM", "Stored incoming message: ${chatMessage.messageId}")
                        }
                        "PAIRING_COMPLETE" -> {
                            val payload = Json.parseToJsonElement(message.payload) as? JsonObject ?: return@collect
                            val serverPairingId = payload["pairingId"]?.jsonPrimitive?.content ?: return@collect
                            val userAId = payload["userAId"]?.jsonPrimitive?.content ?: return@collect
                            val userBId = payload["userBId"]?.jsonPrimitive?.content ?: return@collect

                            val myUserId = preferencesManager.userId
                            val partnerId = if (userAId == myUserId) userBId else userAId

                            pairingRepository.handlePairingComplete(serverPairingId, partnerId)
                            Log.i("ChatListVM", "Pairing complete: $serverPairingId with $partnerId")
                        }
                        "MESSAGE_DELIVERED" -> {
                            Log.i("ChatListVM", "Message delivered: ${message.payload}")
                        }
                        "LOCATION_REQUEST" -> {
                            val payload = Json.parseToJsonElement(message.payload) as? JsonObject ?: return@collect
                            val requesterId = payload["requesterId"]?.jsonPrimitive?.content ?: return@collect
                            Log.i("ChatListVM", "Location request from: $requesterId")
                        }
                        "LOCATION_RESPONSE" -> {
                            Log.i("ChatListVM", "Location response received")
                        }
                        "AUTH_OK" -> {
                            Log.i("ChatListVM", "Authenticated successfully")
                        }
                        "PONG" -> {
                            Log.d("ChatListVM", "Pong received")
                        }
                        else -> {
                            Log.d("ChatListVM", "Unhandled message type: ${message.type}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatListVM", "Error handling incoming message: ${e.message}", e)
                }
            }
        }
    }
}
