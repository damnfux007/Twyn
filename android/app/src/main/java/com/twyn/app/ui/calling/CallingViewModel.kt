package com.twyn.app.ui.calling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twyn.app.data.repository.CallingRepository
import com.twyn.app.data.repository.PairingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallingViewModel @Inject constructor(
    private val callingRepository: CallingRepository,
    private val pairingRepository: PairingRepository
) : ViewModel() {

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _callDuration = MutableStateFlow(0L)
    val callDuration: StateFlow<Long> = _callDuration.asStateFlow()

    private val _partnerName = MutableStateFlow("Contact")
    val partnerName: StateFlow<String> = _partnerName.asStateFlow()

    private var callTimerJob: kotlinx.coroutines.Job? = null

    fun loadPartnerName(pairingId: String) {
        viewModelScope.launch {
            pairingRepository.getAllPairings().first().find { it.pairingId == pairingId }?.let {
                _partnerName.value = it.partnerName
            }
        }
    }

    fun startCall(pairingId: String, callType: String) {
        _callState.value = CallState.CALLING
        callingRepository.sendCallOffer(pairingId, "local_user", callType)

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
