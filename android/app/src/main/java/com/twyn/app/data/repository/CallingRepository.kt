package com.twyn.app.data.repository

import android.content.Context
import com.twyn.app.data.remote.websocket.TwynWebSocketClient
import com.twyn.app.domain.model.WsMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for WebRTC call signaling.
 *
 * WebRTC call flow:
 * 1. User A taps "Call" → generates SDP offer
 * 2. Offer sent to User B via Twyn WebSocket server (signaling only)
 * 3. User B receives offer → generates SDP answer
 * 4. Answer sent back via server
 * 5. ICE candidates exchanged via server
 * 6. Once P2P connection established, media flows directly between devices
 *    (server is no longer in the path)
 *
 * The server only relays signaling messages — no voice/video data touches the server.
 */
@Singleton
class CallingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webSocketClient: TwynWebSocketClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Send a call offer to a paired contact.
     *
     * @param pairingId The pairing to call within
     * @param myUserId This user's ID
     * @param offerSdp The SDP offer string from WebRTC
     */
    fun sendCallOffer(pairingId: String, myUserId: String, offerSdp: String) {
        val wsMessage = WsMessage(
            type = "CALL_OFFER",
            payload = json.encodeToString(mapOf(
                "pairingId" to pairingId,
                "callerId" to myUserId,
                "offerSdp" to offerSdp
            ))
        )
        webSocketClient.send(wsMessage)
    }

    /**
     * Send a call answer (SDP) back to the caller.
     */
    fun sendCallAnswer(pairingId: String, answerSdp: String) {
        val wsMessage = WsMessage(
            type = "CALL_ANSWER",
            payload = json.encodeToString(mapOf(
                "pairingId" to pairingId,
                "answerSdp" to answerSdp
            ))
        )
        webSocketClient.send(wsMessage)
    }

    /**
     * Send an ICE candidate to the peer.
     * ICE candidates are network path discoveries for P2P connectivity.
     */
    fun sendIceCandidate(pairingId: String, candidate: String, sdpMid: String, sdpMLineIndex: Int) {
        val wsMessage = WsMessage(
            type = "CALL_CANDIDATE",
            payload = json.encodeToString(mapOf(
                "pairingId" to pairingId,
                "candidate" to candidate,
                "sdpMid" to sdpMid,
                "sdpMLineIndex" to sdpMLineIndex.toString()
            ))
        )
        webSocketClient.send(wsMessage)
    }

    /**
     * Send a call hangup signal.
     */
    fun sendHangup(pairingId: String) {
        val wsMessage = WsMessage(
            type = "CALL_HANGUP",
            payload = json.encodeToString(mapOf(
                "pairingId" to pairingId
            ))
        )
        webSocketClient.send(wsMessage)
    }

    /**
     * Reject an incoming call.
     */
    fun rejectCall(pairingId: String) {
        val wsMessage = WsMessage(
            type = "CALL_REJECTED",
            payload = json.encodeToString(mapOf(
                "pairingId" to pairingId
            ))
        )
        webSocketClient.send(wsMessage)
    }
}
