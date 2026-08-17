package com.twyn.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain models for the Twyn app.
 * These represent the core data structures used throughout the app.
 */

/** A paired 1-on-1 connection between two users. */
@Serializable
data class Pairing(
    val pairingId: String,
    val partnerId: String,
    val partnerName: String,
    val partnerPhotoUrl: String = "",
    val createdAt: Long,
    val unreadCount: Int = 0,
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0
)

/** A single chat message. Content is always ciphertext until decrypted client-side. */
@Serializable
data class ChatMessage(
    val messageId: String,
    val pairingId: String,
    val senderId: String,
    val ciphertext: String,
    val contentType: ContentType,
    val timestamp: Long,
    val isFromMe: Boolean,
    val isDelivered: Boolean = false,
    val isRead: Boolean = false,
    val isPending: Boolean = false,
    // Decrypted content (populated after decryption, not stored)
    val decryptedText: String? = null,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null
)

/** Types of content that can be sent in a message. */
@Serializable
enum class ContentType {
    TEXT,
    VOICE,
    PHOTO,
    VIDEO,
    FILE,
    LOCATION_REQUEST,
    LOCATION_RESPONSE
}

/** A user profile. */
@Serializable
data class UserProfile(
    val userId: String,
    val displayName: String,
    val bio: String = "",
    val profilePhotoUrl: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0,
    val showOnlineStatus: Boolean = true
)

/** Encryption key pair for a specific pairing. */
data class PairingKeys(
    val publicKey: String,
    val privateKey: String,
    val registrationId: Int
)

/** Media file metadata. */
data class MediaFile(
    val assetId: String,
    val fileName: String,
    val contentType: String,
    val fileSize: Long,
    val thumbnailBase64: String? = null,
    val downloadUrl: String? = null,
    val localPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/** Location data for on-demand sharing. */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long
)

/** WebSocket message envelope. */
@Serializable
data class WsMessage(
    val type: String,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis()
)
