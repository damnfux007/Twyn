package com.twyn.app.services

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileOutputStream
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Drive integration for permanent encrypted media storage.
 *
 * Flow:
 * 1. User signs in with Google (one-time)
 * 2. When media is sent, encrypted file uploaded to server temporarily (24h)
 * 3. Server also pushes encrypted file to sender's own Google Drive
 * 4. After 24h, server deletes the temporary copy
 * 5. Recipient downloads encrypted file from sender's Drive (via server proxy)
 * 6. Only small encrypted thumbnails remain on server permanently
 *
 * Free tier limits (Google Drive API):
 * - 100 GB storage per user (more than enough for encrypted media)
 * - 20,000 queries/day (generous for 5-10 users)
 * - No cost
 */
@Singleton
class GoogleDriveService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val APP_FOLDER_NAME = "Twyn Media"
        private var appFolderId: String? = null
    }

    private var driveService: Drive? = null

    /**
     * Initialize the Drive service with a Google account credential.
     * Called after Google Sign-In succeeds.
     */
    fun initialize(accountEmail: String) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccountName = accountEmail

        driveService = Drive.Builder(
            com.google.api.client.http.javanet.NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Twyn")
            .build()
    }

    /**
     * Upload an encrypted media file to the sender's Google Drive.
     * File is already encrypted client-side before upload.
     *
     * @param localFile The encrypted file to upload
     * @param fileName Display name in Drive
     * @param mimeType MIME type
     * @return Google Drive file ID for future access
     */
    suspend fun uploadEncryptedFile(
        localFile: java.io.File,
        fileName: String,
        mimeType: String
    ): String? {
        return try {
            val service = driveService ?: return null
            val folderId = getOrCreateAppFolder(service)

            val fileMetadata = File().apply {
                name = fileName
                parents = listOf(folderId)
                mimeType = mimeType
            }

            val fileContent = FileContent(mimeType, localFile)
            val uploadedFile = service.files().create(fileMetadata, fileContent)
                .setFields("id")
                .execute()

            uploadedFile.id
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Download an encrypted media file from Google Drive.
     *
     * @param fileId Google Drive file ID
     * @return Local file path to the downloaded encrypted file
     */
    suspend fun downloadFile(fileId: String): java.io.File? {
        return try {
            val service = driveService ?: return null
            val outputFile = java.io.File(context.cacheDir, "drive_$fileId")

            service.files().get(fileId).executeMediaTo(outputFile.outputStream().buffered())
            outputFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the app's dedicated folder in Google Drive.
     * Creates it if it doesn't exist.
     */
    private fun getOrCreateAppFolder(service: Drive): String {
        appFolderId?.let { return it }

        // Search for existing folder
        val result = service.files().list()
            .setQ("name='$APP_FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false")
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()

        if (result.files.isNotEmpty()) {
            appFolderId = result.files[0].id
            return appFolderId!!
        }

        // Create folder
        val folderMetadata = File().apply {
            name = APP_FOLDER_NAME
            mimeType = "application/vnd.google-apps.folder"
        }
        val folder = service.files().create(folderMetadata)
            .setFields("id")
            .execute()

        appFolderId = folder.id
        return appFolderId!!
    }
}
