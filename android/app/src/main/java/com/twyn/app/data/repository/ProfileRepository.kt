package com.twyn.app.data.repository

import com.twyn.app.data.local.database.TwynDatabase
import com.twyn.app.data.remote.websocket.TwynWebSocketClient
import com.twyn.app.domain.model.UserProfile
import com.twyn.app.domain.model.WsMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for user profile operations.
 * Manages the local user's profile and fetches partner profiles.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val database: TwynDatabase,
    private val webSocketClient: TwynWebSocketClient
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var localProfile: UserProfile? = null

    /**
     * Get the local user's profile.
     * Generates a new profile on first launch.
     */
    fun getLocalProfile(): UserProfile {
        return localProfile ?: UserProfile(
            userId = generateUserId(),
            displayName = "Twyn User"
        ).also { localProfile = it }
    }

    /**
     * Update the local user's profile and sync to server.
     */
    suspend fun updateProfile(
        displayName: String? = null,
        bio: String? = null,
        profilePhotoUrl: String? = null,
        showOnlineStatus: Boolean? = null
    ) {
        val current = localProfile ?: return
        localProfile = current.copy(
            displayName = displayName ?: current.displayName,
            bio = bio ?: current.bio,
            profilePhotoUrl = profilePhotoUrl ?: current.profilePhotoUrl,
            showOnlineStatus = showOnlineStatus ?: current.showOnlineStatus
        )

        // Send update to server
        val wsMessage = WsMessage(
            type = "UPDATE_PROFILE",
            payload = json.encodeToString(mapOf(
                "displayName" to (displayName ?: ""),
                "bio" to (bio ?: ""),
                "profilePhotoUrl" to (profilePhotoUrl ?: ""),
                "showOnlineStatus" to (showOnlineStatus ?: true)
            ))
        )
        webSocketClient.send(wsMessage)
    }

    /**
     * Generate a unique user ID.
     * In production, this would be tied to Google Sign-In or a secure registration.
     */
    private fun generateUserId(): String {
        return "user_${java.util.UUID.randomUUID()}"
    }
}
