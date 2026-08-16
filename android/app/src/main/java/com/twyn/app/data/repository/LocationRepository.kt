package com.twyn.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import com.twyn.app.data.remote.websocket.TwynWebSocketClient
import com.twyn.app.domain.model.LocationData
import com.twyn.app.domain.model.WsMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Repository for on-demand location sharing.
 *
 * Location flow:
 * 1. User A taps "Share Location" in a chat
 * 2. Request sent to User B's phone via server
 * 3. User B's phone wakes briefly, grabs a fresh GPS reading
 * 4. Location sent back encrypted to User A
 * 5. User A sees it on a map (OpenStreetMap)
 *
 * No background tracking. No continuous GPS. Minimal battery use.
 * Each location share is a single, on-demand GPS fix.
 */
@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webSocketClient: TwynWebSocketClient
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Request a location share from a paired contact.
     * Sends a request through the server to the partner's phone.
     */
    fun requestLocationFromPartner(pairingId: String, myUserId: String) {
        val wsMessage = WsMessage(
            type = "LOCATION_REQUEST",
            payload = json.encodeToString(mapOf(
                "pairingId" to pairingId,
                "requesterId" to myUserId
            ))
        )
        webSocketClient.send(wsMessage)
    }

    /**
     * Grab a fresh GPS reading and send it back to the requester.
     * Called when this device receives a location request from a paired contact.
     * Uses a single, brief GPS fix — no continuous tracking.
     */
    suspend fun grabAndSendLocation(): LocationData? {
        val location = grabFreshLocation() ?: return null

        val locationData = LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            timestamp = System.currentTimeMillis()
        )

        // Send encrypted location back via WebSocket
        val wsMessage = WsMessage(
            type = "LOCATION_RESPONSE",
            payload = json.encodeToString(locationData)
        )
        webSocketClient.send(wsMessage)

        return locationData
    }

    /**
     * Grab a single fresh GPS fix.
     * Uses high accuracy, single update — minimal battery impact.
     */
    private suspend fun grabFreshLocation(): Location? = suspendCoroutine { cont ->
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cont.resume(null)
            return@suspendCoroutine
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
            .setMaxUpdates(1)                    // Single fix only
            .setDurationMillis(10_000)           // Max 10s to get the fix
            .setWaitForAccurateLocation(true)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedLocationClient.removeLocationUpdates(this)
                if (cont.isActive) cont.resume(result.lastLocation)
            }
        }

        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }
}
