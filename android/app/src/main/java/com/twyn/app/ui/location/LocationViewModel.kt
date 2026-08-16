package com.twyn.app.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twyn.app.data.repository.LocationRepository
import com.twyn.app.domain.model.LocationData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Location Sharing screen.
 * On-demand location sharing — no background tracking.
 */
@HiltViewModel
class LocationViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _partnerLocation = MutableStateFlow<LocationData?>(null)
    val partnerLocation: StateFlow<LocationData?> = _partnerLocation.asStateFlow()

    private val _myLocation = MutableStateFlow<LocationData?>(null)
    val myLocation: StateFlow<LocationData?> = _myLocation.asStateFlow()

    /**
     * Request the partner's location.
     * Sends a request through the server — partner's phone wakes briefly, grabs GPS.
     */
    fun requestPartnerLocation(pairingId: String, myUserId: String) {
        locationRepository.requestLocationFromPartner(pairingId, myUserId)
    }

    /**
     * Share my current location with the partner.
     * Grabs a single, fresh GPS fix — minimal battery use.
     */
    fun shareMyLocation() {
        viewModelScope.launch {
            val location = locationRepository.grabAndSendLocation()
            _myLocation.value = location
        }
    }
}
