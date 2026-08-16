package com.twyn.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twyn.app.data.repository.ProfileRepository
import com.twyn.app.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 * Manages profile editing and app preferences.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _profile = MutableStateFlow(UserProfile(userId = "", displayName = ""))
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    init {
        _profile.value = profileRepository.getLocalProfile()
    }

    fun updateDisplayName(name: String) {
        viewModelScope.launch {
            profileRepository.updateProfile(displayName = name)
            _profile.value = _profile.value.copy(displayName = name)
        }
    }

    fun updateBio(bio: String) {
        viewModelScope.launch {
            profileRepository.updateProfile(bio = bio)
            _profile.value = _profile.value.copy(bio = bio)
        }
    }

    fun toggleOnlineStatus(show: Boolean) {
        viewModelScope.launch {
            profileRepository.updateProfile(showOnlineStatus = show)
            _profile.value = _profile.value.copy(showOnlineStatus = show)
        }
    }
}
