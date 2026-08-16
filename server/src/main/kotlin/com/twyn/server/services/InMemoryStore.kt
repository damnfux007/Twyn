package com.twyn.server.services

import com.twyn.server.models.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * In-memory storage for the Twyn server.
 * For 5-10 users this is perfectly adequate.
 * In production you'd swap this for a persistent database (e.g. SQLite/Postgres).
 */
object InMemoryStore {
    private val mutex = Mutex()

    // userId -> User
    private val users = mutableMapOf<String, User>()

    // pairingId -> Pairing
    private val pairings = mutableMapOf<String, Pairing>()

    // userId -> Set of pairingIds this user belongs to
    private val userPairings = mutableMapOf<String, MutableSet<String>>()

    // pairingId -> list of queued encrypted messages (offline delivery)
    private val pendingMessages = mutableMapOf<String, MutableList<EncryptedMessage>>()

    // assetId -> MediaAsset metadata
    private val mediaAssets = mutableMapOf<String, MediaAsset>()

    // ── User operations ──────────────────────────────────────────────

    suspend fun upsertUser(user: User) = mutex.withLock {
        users[user.userId] = user
    }

    suspend fun getUser(userId: String): User? = mutex.withLock {
        users[userId]
    }

    suspend fun getAllUsers(): List<User> = mutex.withLock {
        users.values.toList()
    }

    // ── Pairing operations ──────────────────────────────────────────

    /**
     * Create a new pairing between two users.
     * Each pairing is a unique 1-on-1 encrypted channel.
     */
    suspend fun createPairing(userAId: String, userBId: String): Pairing = mutex.withLock {
        val pairingId = "pair_${UUID.randomUUID()}"
        val pairing = Pairing(pairingId, userAId, userBId)
        pairings[pairingId] = pairing

        userPairings.getOrPut(userAId) { mutableSetOf() }.add(pairingId)
        userPairings.getOrPut(userBId) { mutableSetOf() }.add(pairingId)

        pairing
    }

    /**
     * Find an existing pairing between two specific users.
     * Prevents duplicate pairings between the same two people.
     */
    suspend fun findPairingBetween(userIdA: String, userIdB: String): Pairing? = mutex.withLock {
        pairings.values.find { p ->
            (p.userAId == userIdA && p.userBId == userIdB) ||
            (p.userAId == userIdB && p.userBId == userIdA)
        }
    }

    /**
     * Get all pairings for a given user — these appear as separate chat threads.
     */
    suspend fun getPairingsForUser(userId: String): List<Pairing> = mutex.withLock {
        val ids = userPairings[userId] ?: emptySet()
        ids.mapNotNull { pairings[it] }
    }

    suspend fun getPairing(pairingId: String): Pairing? = mutex.withLock {
        pairings[pairingId]
    }

    /**
     * Get the other user in a pairing (the paired contact).
     */
    suspend fun getPairedUser(pairingId: String, myUserId: String): String? = mutex.withLock {
        val pairing = pairings[pairingId] ?: return@withLock null
        if (pairing.userAId == myUserId) pairing.userBId else pairing.userAId
    }

    // ── Message operations ──────────────────────────────────────────

    /**
     * Queue an encrypted message for offline delivery.
     * Messages are stored encrypted and deleted once delivered.
     */
    suspend fun queueMessage(message: EncryptedMessage) = mutex.withLock {
        pendingMessages.getOrPut(message.pairingId) { mutableListOf() }.add(message)
    }

    /**
     * Drain all pending messages for a user in a given pairing.
     * Called when the recipient reconnects — messages deleted after retrieval.
     */
    suspend fun drainPendingMessages(pairingId: String): List<EncryptedMessage> = mutex.withLock {
        val messages = pendingMessages.remove(pairingId) ?: emptyList()
        messages
    }

    suspend fun markMessageDelivered(messageId: String) = mutex.withLock {
        pendingMessages.values.forEach { list ->
            list.removeAll { it.messageId == messageId }
        }
    }

    // ── Media operations ────────────────────────────────────────────

    suspend fun storeMediaAsset(asset: MediaAsset) = mutex.withLock {
        mediaAssets[asset.assetId] = asset
    }

    suspend fun getMediaAsset(assetId: String): MediaAsset? = mutex.withLock {
        mediaAssets[assetId]
    }

    /**
     * Delete expired media assets (called periodically).
     */
    suspend fun cleanupExpiredMedia(): Int = mutex.withLock {
        val now = System.currentTimeMillis()
        val expired = mediaAssets.values.filter { it.expiresAt < now }
        expired.forEach { mediaAssets.remove(it.assetId) }
        expired.size
    }
}
