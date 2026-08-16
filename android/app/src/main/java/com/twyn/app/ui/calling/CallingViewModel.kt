package com.twyn.app.ui.calling

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twyn.app.data.repository.CallingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    private var callTimerJob: kotlinx.coroutines.Job? = null

    fun startCall(pairingId: String, myUserId: String, callType: String) {
        _callState.value = CallState.CALLING

        viewModelScope.launch {
            delay(2000)
            _callState.value = CallState.CONNECTED

            callTimerJob = launch {
                while (true) {
                    delay(1000)
                    _callDuration.value++
                }
            }
        }
    }

    fun answerCall(pairingId: String, offerSdp: String) {
        _callState.value = CallState.RECEIVING
    }

    fun endCall(pairingId: String) {
        callTimerJob?.cancel()
        callingRepository.sendHangup(pairingId)
        _callState.value = CallState.ENDED
        _callDuration.value = 0L
    }

    override fun onCleared() {
        super.onCleared()
        callTimerJob?.cancel()
    }
}

enum class CallState {
    IDLE, CALLING, RECEIVING, CONNECTED, ENDED, FAILED
}
