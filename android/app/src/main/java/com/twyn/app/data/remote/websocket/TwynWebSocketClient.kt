package com.twyn.app.data.remote.websocket

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import com.twyn.app.domain.model.WsMessage
import java.util.concurrent.TimeUnit

/**
 * OkHttp-based WebSocket client for Twyn.
 * Handles real-time communication with the Twyn server.
 *
 * Features:
 * - Auto-reconnection with exponential backoff
 * - Message queuing during disconnection
 * - Heartbeat ping/pong for connection health
 * - Flow-based message streaming for reactive UI
 */
class TwynWebSocketClient(
    private val serverUrl: String
) {
    companion object {
        private const val TAG = "TwynWebSocket"
        private const val RECONNECT_BASE_DELAY_MS = 1000L
        private const val RECONNECT_MAX_DELAY_MS = 30000L
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)  // No read timeout for WebSockets
        .pingInterval(HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .build()

    // Incoming messages as a shared Flow
    private val _incomingMessages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 100)
    val incomingMessages: SharedFlow<WsMessage> = _incomingMessages.asSharedFlow()

    // Connection state
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Messages queued for sending when reconnected
    private val pendingOutgoing = Channel<WsMessage>(Channel.BUFFERED)

    private var reconnectAttempt = 0
    private var isAuthenticated = false
    private var authUserId: String? = null

    /**
     * Connect to the WebSocket server and authenticate.
     * Idempotent — calling while already connected/connecting is a no-op.
     */
    fun connect(userId: String) {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.AUTHENTICATED
        ) {
            Log.i(TAG, "Already connected or connecting, skipping")
            return
        }
        authUserId = userId
        _connectionState.value = ConnectionState.CONNECTING

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, createListener())

        // Start reconnection coroutine
        scope.launch { reconnectLoop() }
    }

    /**
     * Send a message through the WebSocket.
     * If not connected, queues it for delivery on reconnect.
     */
    fun send(message: WsMessage) {
        val serialized = json.encodeToString(message)
        val sent = webSocket?.send(serialized) ?: false

        if (!sent && _connectionState.value != ConnectionState.DISCONNECTED) {
            scope.launch {
                pendingOutgoing.send(message)
            }
        }
    }

    /**
     * Disconnect from the server.
     */
    fun disconnect() {
        isAuthenticated = false
        _connectionState.value = ConnectionState.DISCONNECTED
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        scope.cancel()
    }

    /**
     * Handle disconnection and trigger reconnection.
     */
    private fun handleDisconnect() {
        isAuthenticated = false
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Flush queued messages after reconnection.
     */
    private suspend fun flushPendingMessages() {
        while (true) {
            val msg = pendingOutgoing.tryReceive().getOrNull() ?: break
            send(msg)
        }
    }

    /**
     * Exponential backoff reconnection loop.
     */
    private suspend fun reconnectLoop() {
        while (isActive) {
            if (_connectionState.value == ConnectionState.DISCONNECTED && authUserId != null) {
                val delayMs = (RECONNECT_BASE_DELAY_MS * (1 shl reconnectAttempt.coerceAtMost(5)))
                    .coerceAtMost(RECONNECT_MAX_DELAY_MS)
                reconnectAttempt++
                Log.i(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempt)")
                delay(delayMs)
                // Reset state so connect() won't early-return
                _connectionState.value = ConnectionState.DISCONNECTED
                val userId = authUserId ?: return
                authUserId = userId
                _connectionState.value = ConnectionState.CONNECTING

                val request = Request.Builder().url(serverUrl).build()
                webSocket = client.newWebSocket(request, createListener())
            }
            delay(1000)
        }
    }

    private fun createListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WebSocket connected")
            _connectionState.value = ConnectionState.CONNECTED
            reconnectAttempt = 0
            val authMsg = WsMessage(type = "AUTH", payload = authUserId ?: return)
            webSocket.send(json.encodeToString(authMsg))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val message = json.decodeFromString<WsMessage>(text)
                scope.launch { _incomingMessages.emit(message) }
                if (message.type == "AUTH_OK") {
                    isAuthenticated = true
                    _connectionState.value = ConnectionState.AUTHENTICATED
                    scope.launch { flushPendingMessages() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse WebSocket message", e)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WebSocket closing: $code $reason")
            webSocket.close(1000, null)
            handleDisconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WebSocket closed: $code $reason")
            handleDisconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure", t)
            handleDisconnect()
        }
    }

    private val isActive: Boolean
        get() = scope.isActive
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTHENTICATED
}
