package com.twyn.app.data.repository

import com.twyn.app.data.local.database.TwynDatabase
import com.twyn.app.data.remote.websocket.TwynWebSocketClient
import com.twyn.app.domain.model.UserProfile
import com.twyn.app.domain.model.WsMessage
import com.twyn.app.util.PreferencesManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val database: TwynDatabase,
    private val webSocketClient: TwynWebSocketClient,
    private val preferencesManager: PreferencesManager
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var localProfile: UserProfile? = null

    fun getLocalProfile(): UserProfile {
        return localProfile ?: UserProfile(
            userId = preferencesManager.userId,
            displayName = preferencesManager.displayName
        ).also { localProfile = it }
    }

    suspend fun updateProfile(
        displayName: String? = null,
        bio: String? = null,
        profilePhotoUrl: String? = null,
        showOnlineStatus: Boolean? = null
    ) {
        val current = getLocalProfile()
        localProfile = current.copy(
            displayName = displayName ?: current.displayName,
            bio = bio ?: current.bio,
            profilePhotoUrl = profilePhotoUrl ?: current.profilePhotoUrl,
            showOnlineStatus = showOnlineStatus ?: current.showOnlineStatus
        )

        if (displayName != null) preferencesManager.displayName = displayName
        if (bio != null) preferencesManager.bio = bio
        if (profilePhotoUrl != null) preferencesManager.profilePhotoUrl = profilePhotoUrl

        try {
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
        } catch (e: Exception) {
            // Server may not be connected yet
        }
    }
}
