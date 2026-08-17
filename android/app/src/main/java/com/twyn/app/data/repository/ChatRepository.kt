package com.twyn.app.data.repository

import android.util.Log
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

@Singleton
class ChatRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val pairingDao: PairingDao,
    private val webSocketClient: TwynWebSocketClient
) {
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
                    decryptedText = entity.decryptedText,
                    mediaUrl = entity.mediaUrl
                )
            }
        }
    }

    suspend fun sendTextMessage(pairingId: String, plaintext: String, myUserId: String) {
        val messageId = UUID.randomUUID().toString()

        val entity = MessageEntity(
            messageId = messageId,
            pairingId = pairingId,
            senderId = myUserId,
            ciphertext = plaintext,
            contentType = ContentType.TEXT.name,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            isDelivered = false,
            isPending = true,
            decryptedText = plaintext
        )

        messageDao.insertMessage(entity)
        pairingDao.updateLastMessage(pairingId, plaintext, System.currentTimeMillis())

        try {
            val wsMessage = WsMessage(
                type = "SEND_MESSAGE",
                payload = """{"messageId":"$messageId","pairingId":"$pairingId","senderId":"$myUserId","ciphertext":"$plaintext","contentType":"TEXT"}"""
            )
            webSocketClient.send(wsMessage)
        } catch (e: Exception) {
            Log.w("ChatRepo", "WebSocket send failed: ${e.message}")
        }
    }

    suspend fun sendPhotoMessage(pairingId: String, localPath: String, myUserId: String) {
        val messageId = UUID.randomUUID().toString()

        val entity = MessageEntity(
            messageId = messageId,
            pairingId = pairingId,
            senderId = myUserId,
            ciphertext = "",
            contentType = ContentType.PHOTO.name,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            isDelivered = false,
            isPending = true,
            decryptedText = null,
            mediaUrl = localPath
        )

        messageDao.insertMessage(entity)
        pairingDao.updateLastMessage(pairingId, "Photo", System.currentTimeMillis())

        try {
            val wsMessage = WsMessage(
                type = "SEND_MESSAGE",
                payload = """{"messageId":"$messageId","pairingId":"$pairingId","senderId":"$myUserId","ciphertext":"","contentType":"PHOTO","mediaUrl":"$localPath"}"""
            )
            webSocketClient.send(wsMessage)
        } catch (e: Exception) {
            Log.w("ChatRepo", "WebSocket photo send failed: ${e.message}")
        }
    }

    suspend fun markAsRead(pairingId: String) {
        messageDao.markAllRead(pairingId)
    }

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
            decryptedText = message.decryptedText,
            mediaUrl = message.mediaUrl
        )
        messageDao.insertMessage(entity)
        val lastMsg = message.decryptedText ?: message.contentType.name
        pairingDao.updateLastMessage(message.pairingId, lastMsg, message.timestamp)
    }
}
