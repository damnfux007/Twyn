package com.twyn.app.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twyn.app.data.repository.ProfileRepository
import com.twyn.app.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Welcome/Sign-In screen.
 * First-time setup: user enters their name and server URL.
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val prefs: PreferencesManager,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(displayName = name, error = null)
    }

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, error = null)
    }

    fun updateBio(bio: String) {
        _uiState.value = _uiState.value.copy(bio = bio)
    }

    /**
     * Complete sign-in: save profile, server URL, and mark as signed in.
     */
    fun signIn() {
        val state = _uiState.value
        if (state.displayName.isBlank()) {
            _uiState.value = state.copy(error = "Please enter your name")
            return
        }
        if (state.serverUrl.isBlank()) {
            _uiState.value = state.copy(error = "Please enter the server URL")
            return
        }

        viewModelScope.launch {
            // Save server URL
            prefs.serverUrl = state.serverUrl.trim()

            // Save profile
            prefs.displayName = state.displayName.trim()
            prefs.bio = state.bio.trim()
            prefs.isSignedIn = true

            // Create user on server
            val userId = prefs.userId
            profileRepository.updateProfile(
                displayName = state.displayName.trim(),
                bio = state.bio.trim()
            )

            _uiState.value = _uiState.value.copy(isComplete = true)
        }
    }
}

data class WelcomeUiState(
    val displayName: String = "",
    val bio: String = "",
    val serverUrl: String = "wss://twyn-server.onrender.com/ws",
    val error: String? = null,
    val isComplete: Boolean = false
)
