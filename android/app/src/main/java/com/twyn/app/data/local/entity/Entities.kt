package com.twyn.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing encrypted messages locally.
 * Messages are stored encrypted — decryption happens in-memory for display.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val pairingId: String,
    val senderId: String,
    val ciphertext: String,
    val contentType: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val isDelivered: Boolean = false,
    val isRead: Boolean = false,
    val isPending: Boolean = false,
    val decryptedText: String? = null,  // Cached decrypted text (in-memory only in prod)
    val mediaAssetId: String? = null
)

/**
 * Room entity for storing pairing information.
 */
@Entity(tableName = "pairings")
data class PairingEntity(
    @PrimaryKey val pairingId: String,
    val partnerId: String,
    val partnerName: String,
    val partnerPhotoUrl: String = "",
    val createdAt: Long,
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0,
    val unreadCount: Int = 0
)

/**
 * Room entity for storing user profiles.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val bio: String = "",
    val profilePhotoUrl: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0,
    val showOnlineStatus: Boolean = true
)

/**
 * Room entity for storing encryption session state per pairing.
 * Each paired connection has its own Signal Protocol session.
 */
@Entity(tableName = "encryption_sessions")
data class EncryptionSessionEntity(
    @PrimaryKey val pairingId: String,
    val identityKeyPairBase64: String,
    val registrationId: Int,
    val sessionData: String,       // Serialized Signal Protocol session state
    val createdAt: Long = System.currentTimeMillis()
)
