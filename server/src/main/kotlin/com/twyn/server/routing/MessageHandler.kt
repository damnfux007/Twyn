package com.twyn.server.routing

import com.twyn.server.config.ServerConfig
import com.twyn.server.models.*
import com.twyn.server.services.ConnectionManager
import com.twyn.server.services.InMemoryStore
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * WebSocket message handler.
 * Processes all incoming WebSocket frames and routes them by type.
 *
 * Protocol flow:
 * 1. Client connects and sends AUTH with their userId
 * 2. Server registers the connection and confirms with AUTH_OK
 * 3. Client can now send/receive messages for their pairings
 * 4. All message content is ciphertext — server never decrypts
 */
class MessageHandler(private val session: WebSocketSession) {
    private val json = Json { ignoreUnknownKeys = true }
    private var authenticatedUserId: String? = null

    /**
     * Handle an incoming text frame from the client.
     */
    suspend fun handleFrame(frame: Frame.Text) {
        val text = frame.readText()
        val wsMessage = try {
            json.decodeFromString<WsMessage>(text)
        } catch (e: Exception) {
            sendError("Invalid message format")
            return
        }

        when (wsMessage.type) {
            WsMessageType.AUTH -> handleAuth(wsMessage)
            WsMessageType.SEND_MESSAGE -> handleSendMessage(wsMessage)
            WsMessageType.COMPLETE_PAIRING -> handleCompletePairing(wsMessage)
            WsMessageType.UPLOAD_MEDIA -> handleUploadMedia(wsMessage)
            WsMessageType.DOWNLOAD_MEDIA -> handleDownloadMedia(wsMessage)
            WsMessageType.UPDATE_PROFILE -> handleUpdateProfile(wsMessage)
            WsMessageType.LOCATION_REQUEST -> handleLocationRequest(wsMessage)
            WsMessageType.LOCATION_RESPONSE -> handleLocationResponse(wsMessage)
            WsMessageType.TYPING_START, WsMessageType.TYPING_STOP -> handleTyping(wsMessage)
            WsMessageType.MESSAGE_READ -> handleMessageRead(wsMessage)
            WsMessageType.CALL_OFFER, WsMessageType.CALL_ANSWER,
            WsMessageType.CALL_CANDIDATE, WsMessageType.CALL_HANGUP,
            WsMessageType.CALL_REJECTED -> handleCallSignaling(wsMessage)
            WsMessageType.PING -> handlePing()
            else -> sendError("Unhandled message type: ${wsMessage.type}")
        }
    }

    /**
     * Authenticate the WebSocket connection.
     * Client sends their userId; server registers the session.
     */
    private suspend fun handleAuth(message: WsMessage) {
        val userId = message.payload
        authenticatedUserId = userId
        ConnectionManager.register(userId, session)

        // Update user online status
        val user = InMemoryStore.getUser(userId)
        if (user != null) {
            InMemoryStore.upsertUser(user.copy(isOnline = true, lastSeen = System.currentTimeMillis()))
        }

        // Send any pending messages that were queued while offline
        val pairings = InMemoryStore.getPairingsForUser(userId)
        for (pairing in pairings) {
            val pending = InMemoryStore.drainPendingMessages(pairing.pairingId)
            for (msg in pending) {
                if (msg.senderId != userId) {
                    ConnectionManager.sendTo(
                        userId,
                        WsMessage(WsMessageType.INCOMING_MESSAGE, json.encodeToString(msg))
                    )
                }
            }
        }

        send(WsMessage(WsMessageType.AUTH_OK, "ok"))
    }

    /**
     * Handle an encrypted message send request.
     * Server stores it if recipient is offline, or forwards it if online.
     * Server never sees plaintext — content is always ciphertext.
     */
    private suspend fun handleSendMessage(message: WsMessage) {
        val userId = authenticatedUserId ?: return sendError("Not authenticated")
        val encryptedMsg = try {
            json.decodeFromString<EncryptedMessage>(message.payload)
        } catch (e: Exception) {
            return sendError("Invalid message format")
        }

        // Verify the sender is part of this pairing
        val pairing = InMemoryStore.getPairing(encryptedMsg.pairingId)
        if (pairing == null || (pairing.userAId != userId && pairing.userBId != userId)) {
            return sendError("Not authorized for this pairing")
        }

        // Try to deliver immediately to the paired user
        val delivered = ConnectionManager.sendToPairedUser(
            encryptedMsg.pairingId,
            userId,
            WsMessage(WsMessageType.INCOMING_MESSAGE, json.encodeToString(encryptedMsg))
        )

        if (delivered) {
            // Confirm delivery to sender
            send(WsMessage(WsMessageType.MESSAGE_DELIVERED, encryptedMsg.messageId))
        } else {
            // Recipient offline — queue for later delivery
            InMemoryStore.queueMessage(encryptedMsg)
            send(WsMessage(WsMessageType.MESSAGE_DELIVERED, "${encryptedMsg.messageId}:queued"))
        }
    }

    /**
     * Complete a pairing after QR code scanning.
     * Both users must call this endpoint to finalize the pairing.
     */
    private suspend fun handleCompletePairing(message: WsMessage) {
        val userId = authenticatedUserId ?: return sendError("Not authenticated")
        val pairingData = message.payload  // Contains the pairing QR code data

        // QR code format: "twyn_pair:<pairingId>:<userAId>"
        val parts = pairingData.split(":")
        if (parts.size != 3 || parts[0] != "twyn_pair") {
            return sendError("Invalid pairing QR code format")
        }

        val qrPairingId = parts[1]
        val creatorId = parts[2]

        if (creatorId == userId) {
            return sendError("Cannot pair with yourself")
        }

        // Check if pairing already exists
        val existing = InMemoryStore.findPairingBetween(userId, creatorId)
        if (existing != null) {
            send(WsMessage(WsMessageType.PAIRING_EXISTS, existing.pairingId))
            return
        }

        // Create the pairing
        val pairing = InMemoryStore.createPairing(creatorId, userId)

        // Notify both users
        val pairingJson = json.encodeToString(pairing)
        ConnectionManager.sendTo(creatorId, WsMessage(WsMessageType.PAIRING_COMPLETE, pairingJson))
        ConnectionManager.sendTo(userId, WsMessage(WsMessageType.PAIRING_COMPLETE, pairingJson))
    }

    /**
     * Handle media upload notification. Client uploads the encrypted file
     * separately via HTTP, then notifies the server via WebSocket.
     */
    private suspend fun handleUploadMedia(message: WsMessage) {
        val userId = authenticatedUserId ?: return sendError("Not authenticated")
        val asset = try {
            json.decodeFromString<MediaAsset>(message.payload)
        } catch (e: Exception) {
            return sendError("Invalid media asset format")
        }

        InMemoryStore.storeMediaAsset(asset)

        // Notify the paired user that media is available
        ConnectionManager.sendToPairedUser(
            asset.pairingId,
            userId,
            WsMessage(WsMessageType.MEDIA_UPLOADED, json.encodeToString(asset))
        )
    }

    /**
     * Handle media download request. Returns the temporary download URL.
     */
    private suspend fun handleDownloadMedia(message: WsMessage) {
        val assetId = message.payload
        val asset = InMemoryStore.getMediaAsset(assetId)
        if (asset != null) {
            send(WsMessage(WsMessageType.MEDIA_URL, json.encodeToString(asset)))
        } else {
            sendError("Media asset not found or expired")
        }
    }

    /**
     * Handle profile update.
     */
    private suspend fun handleUpdateProfile(message: WsMessage) {
        val userId = authenticatedUserId ?: return sendError("Not authenticated")
        val update = try {
            json.decodeFromString<ProfileUpdate>(message.payload)
        } catch (e: Exception) {
            return sendError("Invalid profile update format")
        }

        val existing = InMemoryStore.getUser(userId) ?: return sendError("User not found")
        val updated = existing.copy(
            displayName = update.displayName ?: existing.displayName,
            bio = update.bio ?: existing.bio,
            profilePhotoUrl = update.profilePhotoUrl ?: existing.profilePhotoUrl,
            showOnlineStatus = update.showOnlineStatus ?: existing.showOnlineStatus
        )
        InMemoryStore.upsertUser(updated)

        send(WsMessage(WsMessageType.PROFILE_UPDATED, json.encodeToString(updated)))
    }

    /**
     * Handle location request — forward to the paired user.
     * On-demand only: recipient's phone wakes briefly, grabs GPS, sends back.
     */
    private suspend fun handleLocationRequest(message: WsMessage) {
        val userId = authenticatedUserId ?: return sendError("Not authenticated")
        // payload: { "pairingId": "...", "requesterId": "..." }
        val data = try {
            json.decodeFromString<Map<String, String>>(message.payload)
        } catch (e: Exception) {
            return sendError("Invalid location request")
        }

        val pairingId = data["pairingId"] ?: return sendError("Missing pairingId")
        ConnectionManager.sendToPairedUser(
            pairingId, userId,
            WsMessage(WsMessageType.LOCATION_REQUEST, json.encodeToString(mapOf("requesterId" to userId)))
        )
    }

    /**
     * Handle location response — forward GPS coordinates to the requester.
     */
    private suspend fun handleLocationResponse(message: WsMessage) {
        val userId = authenticatedUserId ?: return sendError("Not authenticated")
        val location = try {
            json.decodeFromString<LocationData>(message.payload)
        } catch (e: Exception) {
            return sendError("Invalid location data")
        }
        // Forward to all paired users (the requester is identified client-side)
        val pairings = InMemoryStore.getPairingsForUser(userId)
        for (pairing in pairings) {
            ConnectionManager.sendToPairedUser(
                pairing.pairingId, userId,
                WsMessage(WsMessageType.LOCATION_RESPONSE, json.encodeToString(location))
            )
        }
    }

    /**
     * Handle typing indicators — forward to paired user.
     */
    private suspend fun handleTyping(message: WsMessage) {
        val userId = authenticatedUserId ?: return sendError("Not authenticated")
        val pairingId = message.payload
        ConnectionManager.sendToPairedUser(
            pairingId, userId,
            WsMessage(message.type, userId)
        )
    }

    /**
     * Handle message read receipt — forward to sender.
     */
    private suspend fun handleMessageRead(message: WsMessage) {
        val userId = authenticatedUserId ?: return sendError("Not authenticated")
        val pairingId = message.payload
        ConnectionManager.sendToPairedUser(
            pairingId, userId,
            WsMessage(WsMessageType.MESSAGE_READ, userId)
        )
    }

    /**
     * Handle WebRTC call signaling (offer/answer/ICE candidates/hangup).
     * Server acts as a pure relay — no media touches the server.
     */
    private suspend fun handleCallSignaling(message: WsMessage) {
        val userId = authenticatedUserId ?: return sendError("Not authenticated")
        val data = try {
            json.decodeFromString<Map<String, String>>(message.payload)
        } catch (e: Exception) {
            return sendError("Invalid call signaling data")
        }

        val pairingId = data["pairingId"] ?: return sendError("Missing pairingId")
        ConnectionManager.sendToPairedUser(
            pairingId, userId,
            WsMessage(message.type, message.payload)
        )
    }

    /**
     * Heartbeat pong response.
     */
    private suspend fun handlePing() {
        send(WsMessage(WsMessageType.PONG, ""))
    }

    /**
     * Handle disconnection — mark user offline.
     */
    suspend fun handleDisconnect() {
        val userId = authenticatedUserId ?: return
        ConnectionManager.unregister(userId)
        val user = InMemoryStore.getUser(userId)
        if (user != null) {
            InMemoryStore.upsertUser(
                user.copy(isOnline = false, lastSeen = System.currentTimeMillis())
            )
        }
    }

    private suspend fun send(message: WsMessage) {
        val serialized = json.encodeToString(message)
        session.send(Frame.Text(serialized))
    }

    private suspend fun sendError(error: String) {
        send(WsMessage(WsMessageType.AUTH_FAIL, error))
    }
}
