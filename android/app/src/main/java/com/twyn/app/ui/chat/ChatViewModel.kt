package com.twyn.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twyn.app.data.repository.ChatRepository
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
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    /**
     * Get messages for a specific pairing as a reactive Flow.
     */
    fun getMessages(pairingId: String): StateFlow<List<ChatMessage>> {
        return chatRepository.getMessages(pairingId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    /**
     * Send a text message in this pairing.
     * The message is encrypted client-side before being sent.
     */
    fun sendMessage(pairingId: String, text: String, myUserId: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            chatRepository.sendTextMessage(pairingId, text, myUserId)
        }
    }

    /**
     * Mark all messages in this pairing as read.
     */
    fun markAsRead(pairingId: String) {
        viewModelScope.launch {
            chatRepository.markAsRead(pairingId)
        }
    }
}
