package com.twyn.app.ui.media

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twyn.app.data.repository.MediaRepository
import com.twyn.app.domain.model.MediaFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Media Library screen.
 * Shows all media files (photos, videos, voice messages, files)
 * for a specific paired chat, pulled from Google Drive on demand.
 */
@HiltViewModel
class MediaViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _mediaFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    val mediaFiles: StateFlow<List<MediaFile>> = _mediaFiles.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    /**
     * Load media files for a pairing from the server/Drive.
     */
    fun loadMedia(pairingId: String) {
        viewModelScope.launch {
            val files = mediaRepository.getMediaForPairing(pairingId)
            _mediaFiles.value = files
        }
    }

    /**
     * Upload a media file (photo, video, or file) to the pairing.
     * Encrypts the file, uploads to server (temporary), pushes to Google Drive (permanent).
     */
    fun uploadMedia(pairingId: String, uri: Uri, contentType: String) {
        viewModelScope.launch {
            _isUploading.value = true
            mediaRepository.uploadMedia(
                fileUri = uri,
                pairingId = pairingId,
                senderId = "local_user",
                contentType = contentType
            )
            _isUploading.value = false
            loadMedia(pairingId) // Refresh list
        }
    }
}
