package com.twyn.app.ui.calling

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twyn.app.data.repository.CallingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.webrtc.*
import javax.inject.Inject

/**
 * ViewModel for voice/video calling via WebRTC.
 *
 * WebRTC call flow:
 * 1. Initiator creates an SDP offer
 * 2. Offer relayed through Twyn server to the paired contact
 * 3. Responder creates an SDP answer
 * 4. ICE candidates exchanged through server
 * 5. P2P media stream established (server no longer in path)
 *
 * For a small user base (5-10 people), direct P2P works well.
 * TURN/STUN servers used only for NAT traversal.
 */
@HiltViewModel
class CallingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callingRepository: CallingRepository
) : ViewModel() {

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _callDuration = MutableStateFlow(0L)
    val callDuration: StateFlow<Long> = _callDuration.asStateFlow()

    private var peerConnection: PeerConnection? = null
    private var eglBase: EglBase? = null
    private var videoCapturer: VideoCapturer? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null

    /**
     * Initialize WebRTC and start a call.
     */
    fun startCall(pairingId: String, myUserId: String, callType: String) {
        _callState.value = CallState.CALLING

        viewModelScope.launch {
            // Initialize WebRTC
            eglBase = EglBase.create()

            val peerConnectionFactory = initializePeerConnectionFactory()

            // Create peer connection with STUN server for NAT traversal
            val rtcConfig = PeerConnection.RTCConfiguration(
                listOf(
                    // Google's public STUN server — free, works for NAT traversal
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
                )
            )

            peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnectionAdapter() {
                override fun onIceCandidate(candidate: IceCandidate) {
                    callingRepository.sendIceCandidate(
                        pairingId,
                        candidate.sdp,
                        candidate.sdpMid,
                        candidate.sdpMLineIndex
                    )
                }

                override fun onAddStream(stream: MediaStream) {
                    remoteVideoTrack = stream.videoTracks.firstOrNull()
                }

                override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                    if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                        _callState.value = CallState.CONNECTED
                    } else if (state == PeerConnection.PeerConnectionState.DISCONNECTED ||
                               state == PeerConnection.PeerConnectionState.FAILED) {
                        endCall(pairingId)
                    }
                }
            })

            // Add local media stream
            val localStream = peerConnectionFactory.createLocalMediaStream("local")
            val audioTrack = peerConnectionFactory.createAudioTrack("audio", peerConnectionFactory.createAudioSource(MediaConstraints()))
            localStream.addTrack(audioTrack)

            if (callType == "video") {
                val videoSource = peerConnectionFactory.createVideoSource(false)
                videoCapturer = createCameraCapturer(peerConnectionFactory)
                videoCapturer?.initialize(
                    SurfaceTextureHelper.create("CaptureThread", eglBase?.eglBaseContext),
                    context,
                    videoSource.capturerObserver
                )
                videoCapturer?.startCapture(1280, 720, 30)

                localVideoTrack = peerConnectionFactory.createVideoTrack("video", videoSource)
                localStream.addTrack(localVideoTrack)
            }

            peerConnection?.addStream(localStream)

            // Create SDP offer
            val sdpConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", callType == "video"))
            }

            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    peerConnection?.setLocalDescription(this, sdp)
                    callingRepository.sendCallOffer(pairingId, myUserId, sdp.description)
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(msg: String) {
                    _callState.value = CallState.FAILED
                }
                override fun onSetFailure(msg: String) {
                    _callState.value = CallState.FAILED
                }
            }, sdpConstraints)
        }
    }

    /**
     * Handle incoming call offer from paired contact.
     */
    fun answerCall(pairingId: String, offerSdp: String) {
        _callState.value = CallState.RECEIVING
        // Create answer SDP and send back
    }

    /**
     * End the current call.
     */
    fun endCall(pairingId: String) {
        callingRepository.sendHangup(pairingId)
        _callState.value = CallState.ENDED
        peerConnection?.close()
        peerConnection = null
        videoCapturer?.dispose()
        videoCapturer = null
        eglBase?.release()
        eglBase = null
    }

    private fun initializePeerConnectionFactory(): PeerConnectionFactory {
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        return PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase?.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(
                eglBase?.eglBaseContext, true, true
            ))
            .createPeerConnectionFactory()
    }

    private fun createCameraCapturer(factory: PeerConnectionFactory): VideoCapturer {
        val cameraEnumerator = Camera2Enumerator(context)
        val frontCamera = cameraEnumerator.deviceNames.firstOrNull { it.contains("front", ignoreCase = true) }
            ?: cameraEnumerator.deviceNames.first()

        return cameraEnumerator.createCapturer(frontCamera, null)
    }

    override fun onCleared() {
        super.onCleared()
        peerConnection?.dispose()
        videoCapturer?.dispose()
        eglBase?.release()
    }
}

enum class CallState {
    IDLE, CALLING, RECEIVING, CONNECTED, ENDED, FAILED
}

/**
 * Adapter class to avoid implementing all PeerConnection.Observer methods.
 */
private open class PeerConnectionAdapter : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState) {}
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
    override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
    override fun onIceCandidate(candidate: IceCandidate) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
    override fun onAddStream(stream: MediaStream) {}
    override fun onRemoveStream(stream: MediaStream) {}
    override fun onDataChannel(channel: DataChannel) {}
    override fun onRenegotiationNeeded() {}
}
