package com.twyn.server.models

import kotlinx.serialization.Serializable

/**
 * Represents a paired connection between exactly two users.
 * Each pairing has its own unique ID and encryption key material.
 */
@Serializable
data class Pairing(
    val pairingId: String,
    val userAId: String,
    val userBId: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A user registered on the server. Stores only what's needed for routing.
 * No plaintext keys or message content — all encryption is client-side.
 */
@Serializable
data class User(
    val userId: String,
    val displayName: String,
    val bio: String = "",
    val profilePhotoUrl: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val showOnlineStatus: Boolean = true,
    val pushToken: String? = null
)

/**
 * An encrypted message envelope. The server stores this temporarily
 * until the recipient downloads it, then deletes it.
 * content is always ciphertext — the server never sees plaintext.
 */
@Serializable
data class EncryptedMessage(
    val messageId: String,
    val pairingId: String,
    val senderId: String,
    val ciphertext: String,          // Base64-encoded Signal Protocol ciphertext
    val contentType: ContentType,
    val timestamp: Long = System.currentTimeMillis(),
    val delivered: Boolean = false
)

/**
 * Types of content that can be sent in a message.
 */
@Serializable
enum class ContentType {
    TEXT,
    VOICE,
    PHOTO,
    VIDEO,
    FILE,
    LOCATION_REQUEST,
    LOCATION_RESPONSE,
    CALL_OFFER,
    CALL_ANSWER,
    CALL_CANDIDATE,
    CALL_HANGUP
}

/**
 * Temporary media storage metadata. Actual file stored on server temporarily,
 * permanent copy goes to sender's Google Drive.
 */
@Serializable
data class MediaAsset(
    val assetId: String,
    val pairingId: String,
    val senderId: String,
    val fileName: String,
    val contentType: String,
    val fileSize: Long,
    val encryptedThumbnailBase64: String?,  // Small encrypted thumbnail, kept permanently
    val serverDownloadUrl: String?,         // Temporary URL, deleted after 24h
    val driveFileId: String?,              // Permanent Google Drive file ID
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 24 * 60 * 60 * 1000 // 24 hours
)

/**
 * WebSocket message envelope for real-time communication.
 * All messages between client and server are wrapped in this format.
 */
@Serializable
data class WsMessage(
    val type: WsMessageType,
    val payload: String,         // JSON-serialized payload specific to message type
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class WsMessageType {
    // Authentication
    AUTH,
    AUTH_OK,
    AUTH_FAIL,

    // Messaging
    SEND_MESSAGE,
    MESSAGE_DELIVERED,
    INCOMING_MESSAGE,
    MESSAGE_READ,

    // Pairing
    GENERATE_PAIRING_QR,
    PAIRING_QR_RESPONSE,
    COMPLETE_PAIRING,
    PAIRING_COMPLETE,
    PAIRING_EXISTS,

    // Media
    UPLOAD_MEDIA,
    MEDIA_UPLOADED,
    DOWNLOAD_MEDIA,
    MEDIA_URL,

    // Typing / presence
    TYPING_START,
    TYPING_STOP,
    USER_ONLINE,
    USER_OFFLINE,

    // Location
    LOCATION_REQUEST,
    LOCATION_RESPONSE,

    // Calling (WebRTC signaling)
    CALL_OFFER,
    CALL_ANSWER,
    CALL_CANDIDATE,
    CALL_HANGUP,
    CALL_REJECTED,

    // Profile
    UPDATE_PROFILE,
    PROFILE_UPDATED,

    // Heartbeat
    PING,
    PONG
}

/**
 * Profile update payload.
 */
@Serializable
data class ProfileUpdate(
    val displayName: String? = null,
    val bio: String? = null,
    val profilePhotoUrl: String? = null,
    val showOnlineStatus: Boolean? = null
)

/**
 * Location data for on-demand sharing.
 */
@Serializable
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long
)
