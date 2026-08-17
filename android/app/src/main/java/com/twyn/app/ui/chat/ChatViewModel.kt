package com.twyn.app.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twyn.app.data.repository.ChatRepository
import com.twyn.app.data.repository.PairingRepository
import com.twyn.app.domain.model.ChatMessage
import com.twyn.app.domain.model.ContentType
import com.twyn.app.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val pairingRepository: PairingRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _partnerName = MutableStateFlow("Chat")
    val partnerName: StateFlow<String> = _partnerName.asStateFlow()

    private val messageFlows = mutableMapOf<String, StateFlow<List<ChatMessage>>>()

    fun getMyUserId(): String = preferencesManager.userId

    fun getMessages(pairingId: String): StateFlow<List<ChatMessage>> {
        return messageFlows.getOrPut(pairingId) {
            chatRepository.getMessages(pairingId)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList()
                )
        }
    }

    fun loadPartnerName(pairingId: String) {
        viewModelScope.launch {
            pairingRepository.getAllPairings().first().find { it.pairingId == pairingId }?.let {
                _partnerName.value = it.partnerName
            }
        }
    }

    fun sendMessage(pairingId: String, text: String) {
        if (text.isBlank()) return
        val userId = preferencesManager.userId
        viewModelScope.launch {
            try {
                chatRepository.sendTextMessage(pairingId, text, userId)
            } catch (e: Exception) {
                Log.e("ChatVM", "Failed to send message", e)
            }
        }
    }

    fun sendPhoto(pairingId: String, localPath: String) {
        val userId = preferencesManager.userId
        viewModelScope.launch {
            try {
                chatRepository.sendPhotoMessage(pairingId, localPath, userId)
            } catch (e: Exception) {
                Log.e("ChatVM", "Failed to send photo", e)
            }
        }
    }

    fun markAsRead(pairingId: String) {
        viewModelScope.launch {
            chatRepository.markAsRead(pairingId)
        }
    }
}
