package com.twyn.app.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twyn.app.data.repository.ChatRepository
import com.twyn.app.data.repository.PairingRepository
import com.twyn.app.domain.model.Pairing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * ViewModel for the Chat List (home) screen.
 * Shows all active 1-on-1 paired chats.
 */
@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    val pairings: StateFlow<List<Pairing>> = pairingRepository.getAllPairings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
