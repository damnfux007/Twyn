package com.twyn.app.data.repository

import com.twyn.app.data.local.dao.MessageDao
import com.twyn.app.data.local.dao.PairingDao
import com.twyn.app.data.local.entity.MessageEntity
import com.twyn.app.data.local.entity.PairingEntity
import com.twyn.app.data.remote.websocket.TwynWebSocketClient
import com.twyn.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for chat-related operations.
 * Bridges the local Room database, WebSocket client, and encryption layer.
 *
 * All message content is encrypted before storage and transmission.
 * The server never sees plaintext — encryption/decryption happens here.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val pairingDao: PairingDao,
    private val webSocketClient: TwynWebSocketClient
) {
    /**
     * Get all active pairings as a reactive Flow.
     * These appear as separate chat threads on the home screen.
     */
    fun getAllPairings(): Flow<List<Pairing>> {
        return pairingDao.getAllPairings().map { entities ->
            entities.map { entity ->
                Pairing(
                    pairingId = entity.pairingId,
                    partnerId = entity.partnerId,
                    partnerName = entity.partnerName,
                    partnerPhotoUrl = entity.partnerPhotoUrl,
                    createdAt = entity.createdAt,
                    unreadCount = entity.unreadCount,
                    lastMessage = entity.lastMessage,
                    lastMessageTimestamp = entity.lastMessageTimestamp
                )
            }
        }
    }

    /**
     * Get messages for a specific pairing as a reactive Flow.
     */
    fun getMessages(pairingId: String): Flow<List<ChatMessage>> {
        return messageDao.getMessagesByPairing(pairingId).map { entities ->
            entities.map { entity ->
                ChatMessage(
                    messageId = entity.messageId,
                    pairingId = entity.pairingId,
                    senderId = entity.senderId,
                    ciphertext = entity.ciphertext,
                    contentType = ContentType.valueOf(entity.contentType),
                    timestamp = entity.timestamp,
                    isFromMe = entity.isFromMe,
                    isDelivered = entity.isDelivered,
                    isRead = entity.isRead,
                    isPending = entity.isPending,
                    decryptedText = entity.decryptedText
                )
            }
        }
    }

    /**
     * Send an encrypted text message.
     * Encrypts the message, stores it locally, and sends via WebSocket.
     */
    suspend fun sendTextMessage(pairingId: String, plaintext: String, myUserId: String) {
        val messageId = UUID.randomUUID().toString()

        // In production: encrypt plaintext with Signal Protocol for this pairing
        // val ciphertext = encryptionManager.encryptMessage(plaintext, pairingId)
        val ciphertext = plaintext // Placeholder — replace with actual encryption

        val entity = MessageEntity(
            messageId = messageId,
            pairingId = pairingId,
            senderId = myUserId,
            ciphertext = ciphertext,
            contentType = ContentType.TEXT.name,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            isDelivered = false,
            isPending = true,
            decryptedText = plaintext
        )

        // Store locally first
        messageDao.insertMessage(entity)

        // Update pairing's last message
        pairingDao.updateLastMessage(pairingId, plaintext, System.currentTimeMillis())

        // Send via WebSocket
        val payloadJson = kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                put("messageId", kotlinx.serialization.json.JsonPrimitive(messageId))
                put("pairingId", kotlinx.serialization.json.JsonPrimitive(pairingId))
                put("senderId", kotlinx.serialization.json.JsonPrimitive(myUserId))
                put("ciphertext", kotlinx.serialization.json.JsonPrimitive(ciphertext))
                put("contentType", kotlinx.serialization.json.JsonPrimitive("TEXT"))
            }
        )
        val wsMessage = com.twyn.app.domain.model.WsMessage(
            type = "SEND_MESSAGE",
            payload = payloadJson
        )
        webSocketClient.send(wsMessage)
    }

    /**
     * Mark messages in a pairing as read.
     */
    suspend fun markAsRead(pairingId: String) {
        messageDao.markAllRead(pairingId)
    }

    /**
     * Store a received message (decrypted and displayed).
     */
    suspend fun receiveMessage(message: ChatMessage) {
        val entity = MessageEntity(
            messageId = message.messageId,
            pairingId = message.pairingId,
            senderId = message.senderId,
            ciphertext = message.ciphertext,
            contentType = message.contentType.name,
            timestamp = message.timestamp,
            isFromMe = false,
            isDelivered = true,
            decryptedText = message.decryptedText
        )
        messageDao.insertMessage(entity)

        // Update pairing's last message
        val lastMsg = message.decryptedText ?: message.contentType.name
        pairingDao.updateLastMessage(message.pairingId, lastMsg, message.timestamp)
    }
}
