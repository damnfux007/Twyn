package com.twyn.app.data.repository

import android.content.Context
import android.net.Uri
import com.twyn.app.data.local.database.TwynDatabase
import com.twyn.app.data.remote.websocket.TwynWebSocketClient
import com.twyn.app.domain.model.MediaFile
import com.twyn.app.domain.model.WsMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for media operations (photos, videos, voice messages, files).
 *
 * Media flow:
 * 1. User selects/sends media
 * 2. App encrypts the file locally (AES-256)
 * 3. Encrypted file uploaded to server via HTTP POST
 * 4. Server stores temporarily (24h), returns asset ID
 * 5. Server also pushes permanent copy to sender's Google Drive
 * 6. Recipient downloads encrypted file from server
 * 7. Recipient decrypts locally
 * 8. After 24h, server deletes the temporary copy
 *
 * Small encrypted thumbnails are kept on the server permanently
 * for fast previews in the chat list and media library.
 */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: TwynDatabase,
    private val webSocketClient: TwynWebSocketClient
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Get a local cache directory for encrypted media files.
     */
    private fun getMediaCacheDir(): File {
        val dir = File(context.cacheDir, "media")
        dir.mkdirs()
        return dir
    }

    /**
     * Encrypt and upload a media file to the server.
     *
     * @param fileUri The local file URI (photo, video, voice recording, etc.)
     * @param pairingId The pairing this media belongs to
     * @param senderId The local user's ID
     * @param contentType MIME type (e.g., "image/jpeg", "audio/opus")
     * @return The asset ID if upload succeeds
     */
    suspend fun uploadMedia(
        fileUri: Uri,
        pairingId: String,
        senderId: String,
        contentType: String
    ): Result<MediaFile> {
        return try {
            val fileName = getFileName(fileUri)
            val assetId = "media_${UUID.randomUUID()}"

            // In production: encrypt file with AES-256 before upload
            // val encryptedBytes = encryptFile(fileUri)

            // Upload via HTTP to server
            // In production: use OkHttp multipart upload to /api/media/upload
            // val response = httpClient.newCall(uploadRequest).execute()

            val mediaFile = MediaFile(
                assetId = assetId,
                fileName = fileName,
                contentType = contentType,
                fileSize = getFileSize(fileUri),
                timestamp = System.currentTimeMillis()
            )

            // Notify paired user via WebSocket
            val wsMessage = WsMessage(
                type = "UPLOAD_MEDIA",
                payload = json.encodeToString(mapOf(
                    "assetId" to assetId,
                    "pairingId" to pairingId,
                    "senderId" to senderId,
                    "fileName" to fileName,
                    "contentType" to contentType
                ))
            )
            webSocketClient.send(wsMessage)

            Result.success(mediaFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download and decrypt a media file from the server.
     */
    suspend fun downloadMedia(assetId: String): Result<File> {
        return try {
            // Request download URL from server
            val wsMessage = WsMessage(type = "DOWNLOAD_MEDIA", payload = assetId)
            webSocketClient.send(wsMessage)

            // In production: download from server URL, decrypt, return local file
            val cacheDir = getMediaCacheDir()
            val localFile = File(cacheDir, assetId)

            Result.success(localFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get media files for a specific pairing (from local cache).
     */
    suspend fun getMediaForPairing(pairingId: String): List<MediaFile> {
        // In production: query server or local database for media metadata
        return emptyList()
    }

    private fun getFileName(uri: Uri): String {
        var name = "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) {
                size = cursor.getLong(sizeIndex)
            }
        }
        return size
    }
}
