package com.twyn.app.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twyn.app.data.repository.LocationRepository
import com.twyn.app.domain.model.LocationData
import com.twyn.app.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _partnerLocation = MutableStateFlow<LocationData?>(null)
    val partnerLocation: StateFlow<LocationData?> = _partnerLocation.asStateFlow()

    private val _myLocation = MutableStateFlow<LocationData?>(null)
    val myLocation: StateFlow<LocationData?> = _myLocation.asStateFlow()

    fun requestPartnerLocation(pairingId: String) {
        val myUserId = preferencesManager.userId
        locationRepository.requestLocationFromPartner(pairingId, myUserId)
    }

    fun shareMyLocation() {
        viewModelScope.launch {
            val location = locationRepository.grabAndSendLocation()
            _myLocation.value = location
        }
    }
}
