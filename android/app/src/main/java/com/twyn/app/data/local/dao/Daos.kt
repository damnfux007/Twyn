package com.twyn.app.data.local.dao

import androidx.room.*
import com.twyn.app.data.local.entity.MessageEntity
import com.twyn.app.data.local.entity.PairingEntity
import com.twyn.app.data.local.entity.UserEntity
import com.twyn.app.data.local.entity.EncryptionSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Objects for the local Room database.
 * Provides reactive queries via Flow for UI updates.
 */
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE pairingId = :pairingId ORDER BY timestamp ASC")
    fun getMessagesByPairing(pairingId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("UPDATE messages SET isDelivered = 1 WHERE messageId = :messageId")
    suspend fun markDelivered(messageId: String)

    @Query("UPDATE messages SET isRead = 1 WHERE pairingId = :pairingId AND isFromMe = 0")
    suspend fun markAllRead(pairingId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE pairingId = :pairingId AND isFromMe = 0 AND isRead = 0")
    suspend fun getUnreadCount(pairingId: String): Int

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun deleteMessage(messageId: String)
}

@Dao
interface PairingDao {
    @Query("SELECT * FROM pairings ORDER BY lastMessageTimestamp DESC")
    fun getAllPairings(): Flow<List<PairingEntity>>

    @Query("SELECT * FROM pairings WHERE pairingId = :pairingId")
    suspend fun getPairing(pairingId: String): PairingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPairing(pairing: PairingEntity)

    @Update
    suspend fun updatePairing(pairing: PairingEntity)

    @Query("UPDATE pairings SET lastMessage = :message, lastMessageTimestamp = :timestamp WHERE pairingId = :pairingId")
    suspend fun updateLastMessage(pairingId: String, message: String, timestamp: Long)

    @Query("UPDATE pairings SET partnerName = :name WHERE partnerId = :partnerId")
    suspend fun updatePartnerNameByPartnerId(partnerId: String, name: String)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUser(userId: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)
}

@Dao
interface EncryptionSessionDao {
    @Query("SELECT * FROM encryption_sessions WHERE pairingId = :pairingId")
    suspend fun getSession(pairingId: String): EncryptionSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: EncryptionSessionEntity)

    @Delete
    suspend fun deleteSession(session: EncryptionSessionEntity)
}
