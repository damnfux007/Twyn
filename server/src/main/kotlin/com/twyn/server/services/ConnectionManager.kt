package com.twyn.server.services

import com.twyn.server.models.*
import io.ktor.websocket.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages all active WebSocket connections.
 * Each connected user has exactly one WebSocket session.
 * Routes messages to the correct recipient based on pairing membership.
 */
object ConnectionManager {
    private val json = Json { ignoreUnknownKeys = true }

    // userId -> WebSocket session
    private val sessions = mutableMapOf<String, WebSocketSession>()
    private val mutex = Mutex()

    /**
     * Register a new WebSocket session for a user.
     * Called when a client authenticates over WebSocket.
     */
    suspend fun register(userId: String, session: WebSocketSession) = mutex.withLock {
        sessions[userId] = session
    }

    /**
     * Remove a user's WebSocket session on disconnect.
     */
    suspend fun unregister(userId: String) = mutex.withLock {
        sessions.remove(userId)
    }

    /**
     * Check if a user is currently connected.
     */
    suspend fun isConnected(userId: String): Boolean = mutex.withLock {
        sessions.containsKey(userId)
    }

    /**
     * Get a user's active WebSocket session.
     */
    suspend fun getSession(userId: String): WebSocketSession? = mutex.withLock {
        sessions[userId]
    }

    /**
     * Get all connected user IDs.
     */
    suspend fun getConnectedUsers(): Set<String> = mutex.withLock {
        sessions.keys.toSet()
    }

    /**
     * Send a typed WebSocket message to a specific user.
     * Returns false if the user is not connected.
     */
    suspend fun sendTo(userId: String, message: WsMessage): Boolean {
        val session = getSession(userId) ?: return false
        return try {
            val serialized = json.encodeToString(message)
            session.send(Frame.Text(serialized))
            true
        } catch (e: Exception) {
            // Connection is dead, clean it up
            unregister(userId)
            false
        }
    }

    /**
     * Send a message to the other person in a pairing.
     * This is the primary routing method — find the paired user, then send to them.
     */
    suspend fun sendToPairedUser(pairingId: String, senderId: String, message: WsMessage): Boolean {
        val pairedUserId = InMemoryStore.getPairedUser(pairingId, senderId) ?: return false
        return sendTo(pairedUserId, message)
    }

    /**
     * Broadcast to all connected users (used for presence updates).
     */
    suspend fun broadcast(message: WsMessage, excludeUserId: String? = null) {
        val serialized = json.encodeToString(message)
        mutex.withLock {
            sessions.entries.toList()
        }.forEach { (userId, session) ->
            if (userId != excludeUserId) {
                try {
                    session.send(Frame.Text(serialized))
                } catch (_: Exception) { }
            }
        }
    }
}
