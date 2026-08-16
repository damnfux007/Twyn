package com.twyn.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.twyn.app.encryption.EncryptionManager
import com.twyn.app.data.local.dao.PairingDao
import com.twyn.app.data.local.entity.PairingEntity
import com.twyn.app.data.remote.websocket.TwynWebSocketClient
import com.twyn.app.domain.model.Pairing
import com.twyn.app.domain.model.WsMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for pairing/QR code operations.
 *
 * Pairing flow:
 * 1. User A taps "Pair New Contact" → generates QR code with pre-key bundle
 * 2. User B scans QR code → sends pairing request to server
 * 3. Server creates pairing, notifies both users
 * 4. Both users' apps create a Signal Protocol session for this pairing
 * 5. From this point, all messages in this pairing are independently encrypted
 */
@Singleton
class PairingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptionManager: EncryptionManager,
    private val pairingDao: PairingDao,
    private val webSocketClient: TwynWebSocketClient
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Generate a QR code bitmap for pairing.
     *
     * The QR code contains:
     * - App identifier ("twyn")
     * - The user's ID
     * - A pre-key bundle (base64) for Signal Protocol key exchange
     * - A unique pairing token
     *
     * Format: "twyn:<userId>:<preKeyBundleBase64>:<pairingToken>"
     */
    fun generatePairingQrCode(userId: String): Bitmap {
        val bundle = encryptionManager.generatePreKeyBundle()
        val bundleBase64 = encryptionManager.encodePreKeyBundleForQr(bundle)
        val pairingToken = UUID.randomUUID().toString().take(8)

        val qrContent = "twyn:$userId:$bundleBase64:$pairingToken"

        // Generate QR code using ZXing
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(qrContent, BarcodeFormat.QR_MATRIX, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return bitmap
    }

    /**
     * Process a scanned QR code to initiate pairing.
     *
     * Called after scanning either via camera or from gallery image.
     * Parses the QR content, extracts the pre-key bundle, and completes pairing.
     */
    suspend fun processScannedQrCode(qrContent: String, myUserId: String): Result<Pairing> {
        return try {
            val parts = qrContent.split(":")
            if (parts.size < 3 || parts[0] != "twyn") {
                return Result.failure(Exception("Invalid Twyn QR code"))
            }

            val partnerId = parts[1]
            val preKeyBundleBase64 = parts[2]

            if (partnerId == myUserId) {
                return Result.failure(Exception("Cannot pair with yourself"))
            }

            // Check if already paired
            val existing = pairingDao.getPairing("${partnerId}_$myUserId")
                ?: pairingDao.getPairing("${myUserId}_$partnerId")
            if (existing != null) {
                return Result.failure(Exception("Already paired with this contact"))
            }

            // Decode partner's pre-key bundle
            val preKeyBundle = encryptionManager.decodePreKeyBundleFromQr(preKeyBundleBase64)

            // Create a Signal Protocol session with the partner
            // In production: use SessionBuilder to establish the session with the pre-key

            // Send pairing completion to server
            val pairingId = UUID.randomUUID().toString()
            val wsMessage = WsMessage(
                type = "COMPLETE_PAIRING",
                payload = json.encodeToString(mapOf(
                    "pairingId" to pairingId,
                    "partnerId" to partnerId,
                    "preKeyBundle" to preKeyBundleBase64
                ))
            )
            webSocketClient.send(wsMessage)

            // Store pairing locally
            val pairing = Pairing(
                pairingId = pairingId,
                partnerId = partnerId,
                partnerName = "Contact ${partnerId.take(6)}", // Temporary name, updated when profile arrives
                createdAt = System.currentTimeMillis()
            )

            val entity = PairingEntity(
                pairingId = pairingId,
                partnerId = partnerId,
                partnerName = pairing.partnerName,
                createdAt = pairing.createdAt
            )
            pairingDao.insertPairing(entity)

            Result.success(pairing)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all active pairings.
     */
    fun getAllPairings(): Flow<List<Pairing>> {
        return pairingDao.getAllPairings().map { entities ->
            entities.map { entity ->
                Pairing(
                    pairingId = entity.pairingId,
                    partnerId = entity.partnerId,
                    partnerName = entity.partnerName,
                    partnerPhotoUrl = entity.partnerPhotoUrl,
                    createdAt = entity.createdAt,
                    unreadCount = entity.unreadCount,
                    lastMessage = entity.lastMessage,
                    lastMessageTimestamp = entity.lastMessageTimestamp
                )
            }
        }
    }
}
