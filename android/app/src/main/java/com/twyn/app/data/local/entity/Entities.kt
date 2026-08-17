package com.twyn.app.data.local.entity

import androidx.room.*

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
    val decryptedText: String? = null,
    val mediaUrl: String? = null,
    val thumbnailUrl: String? = null
)

@Entity(tableName = "pairings")
data class PairingEntity(
    @PrimaryKey val pairingId: String,
    val partnerId: String,
    val partnerName: String,
    val partnerPhotoUrl: String = "",
    val createdAt: Long,
    val unreadCount: Int = 0,
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val bio: String = "",
    val profilePhotoUrl: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0
)

@Entity(tableName = "encryption_sessions")
data class EncryptionSessionEntity(
    @PrimaryKey val pairingId: String,
    val publicKey: String,
    val privateKey: String,
    val registrationId: Int,
    val createdAt: Long = System.currentTimeMillis()
)
