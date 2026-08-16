package com.twyn.app.util

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility for managing persistent app preferences.
 * Uses DataStore/SharedPreferences for user settings.
 */
@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("twyn_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_BIO = "bio"
        private const val KEY_PROFILE_PHOTO = "profile_photo_url"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_SHOW_ONLINE = "show_online_status"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_GOOGLE_ACCOUNT = "google_account_email"
    }

    var userId: String
        get() = prefs.getString(KEY_USER_ID, null) ?: generateAndStoreUserId()
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, "Twyn User") ?: "Twyn User"
        set(value) = prefs.edit().putString(KEY_DISPLAY_NAME, value).apply()

    var bio: String
        get() = prefs.getString(KEY_BIO, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BIO, value).apply()

    var profilePhotoUrl: String
        get() = prefs.getString(KEY_PROFILE_PHOTO, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PROFILE_PHOTO, value).apply()

    var isDarkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()

    var showOnlineStatus: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ONLINE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_ONLINE, value).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "ws://YOUR_SERVER_IP:8080/ws") ?: "ws://YOUR_SERVER_IP:8080/ws"
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var googleAccountEmail: String?
        get() = prefs.getString(KEY_GOOGLE_ACCOUNT, null)
        set(value) = prefs.edit().putString(KEY_GOOGLE_ACCOUNT, value).apply()

    private fun generateAndStoreUserId(): String {
        val id = "twyn_${Settings.Secure.ANDROID_ID}_${System.currentTimeMillis()}"
        prefs.edit().putString(KEY_USER_ID, id).apply()
        return id
    }
}
