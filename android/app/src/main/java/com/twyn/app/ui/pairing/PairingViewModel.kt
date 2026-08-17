package com.twyn.app.ui.pairing

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twyn.app.data.repository.PairingRepository
import com.twyn.app.data.repository.ProfileRepository
import com.twyn.app.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
    private val profileRepository: ProfileRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    init {
        generateQrCode()
    }

    private fun generateQrCode() {
        viewModelScope.launch {
            try {
                val profile = profileRepository.getLocalProfile()
                val bitmap = pairingRepository.generatePairingQrCode(profile.userId, profile.displayName)
                _uiState.value = _uiState.value.copy(
                    qrBitmap = bitmap,
                    pairingCode = profile.userId.take(8).uppercase()
                )
            } catch (e: Exception) {
                Log.e("PairingViewModel", "Failed to generate QR code", e)
                _uiState.value = _uiState.value.copy(statusMessage = "Failed to generate QR code")
            }
        }
    }

    fun processScannedQr(qrContent: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val userId = profileRepository.getLocalProfile().userId
                val result = pairingRepository.processScannedQrCode(qrContent, userId)
                result.onSuccess {
                    _uiState.value = _uiState.value.copy(isSuccess = true, statusMessage = "Pairing successful!")
                    onComplete()
                }.onFailure { e ->
                    Log.e("PairingViewModel", "Pairing failed", e)
                    _uiState.value = _uiState.value.copy(statusMessage = e.message ?: "Pairing failed")
                }
            } catch (e: Exception) {
                Log.e("PairingViewModel", "Unexpected error during pairing", e)
                _uiState.value = _uiState.value.copy(statusMessage = "Error: ${e.message}")
            }
        }
    }
}

data class PairingUiState(
    val qrBitmap: Bitmap? = null,
    val pairingCode: String? = null,
    val statusMessage: String? = null,
    val isSuccess: Boolean = false
)
