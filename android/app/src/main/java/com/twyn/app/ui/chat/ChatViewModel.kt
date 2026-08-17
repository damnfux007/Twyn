package com.twyn.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twyn.app.data.repository.ChatRepository
import com.twyn.app.data.repository.PairingRepository
import com.twyn.app.domain.model.ChatMessage
import com.twyn.app.domain.model.ContentType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for an individual 1-on-1 chat screen.
 * Handles message sending, receiving, and encryption.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val pairingRepository: PairingRepository
) : ViewModel() {

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _partnerName = MutableStateFlow("Chat")
    val partnerName: StateFlow<String> = _partnerName.asStateFlow()

    fun loadPartnerName(pairingId: String) {
        viewModelScope.launch {
            pairingRepository.getAllPairings().first().find { it.pairingId == pairingId }?.let {
                _partnerName.value = it.partnerName
            }
        }
    }

    fun getMessages(pairingId: String): StateFlow<List<ChatMessage>> {
        return chatRepository.getMessages(pairingId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    fun sendMessage(pairingId: String, text: String, myUserId: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            chatRepository.sendTextMessage(pairingId, text, myUserId)
        }
    }

    fun markAsRead(pairingId: String) {
        viewModelScope.launch {
            chatRepository.markAsRead(pairingId)
        }
    }
}
