package com.twyn.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twyn.app.data.repository.ProfileRepository
import com.twyn.app.domain.model.UserProfile
import com.twyn.app.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _profile = MutableStateFlow(UserProfile(userId = "", displayName = ""))
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    val isDarkTheme: StateFlow<Boolean> = preferencesManager.darkThemeFlow
    val chatThemeIndex: StateFlow<Int> = preferencesManager.chatThemeIndexFlow

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

    fun updateProfilePhoto(localPath: String) {
        preferencesManager.profilePhotoUrl = localPath
        _profile.value = _profile.value.copy(profilePhotoUrl = localPath)
    }

    fun toggleDarkTheme(enabled: Boolean) {
        preferencesManager.isDarkTheme = enabled
    }

    fun setChatTheme(index: Int) {
        preferencesManager.chatThemeIndex = index
    }
}
