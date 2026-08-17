package com.twyn.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
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
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptionManager: EncryptionManager,
    private val pairingDao: PairingDao,
    private val webSocketClient: TwynWebSocketClient
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun generatePairingQrCode(userId: String, displayName: String = ""): Bitmap {
        val bundle = encryptionManager.generatePreKeyBundle()
        val bundleBase64 = encryptionManager.encodePreKeyBundleForQr(bundle)
        val pairingToken = UUID.randomUUID().toString().take(8)

        // twyn:userId:bundleBase64:pairingToken:displayName
        val qrContent = "twyn:$userId:$bundleBase64:$pairingToken:$displayName"

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, 512, 512)
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

    suspend fun processScannedQrCode(qrContent: String, myUserId: String): Result<Pairing> {
        return try {
            val parts = qrContent.split(":")
            if (parts.size < 3 || parts[0] != "twyn") {
                return Result.failure(Exception("Invalid Twyn QR code"))
            }

            val partnerId = parts[1]
            val preKeyBundleBase64 = parts[2]
            val partnerName = if (parts.size >= 5 && parts[4].isNotBlank()) parts[4] else "Contact ${partnerId.take(8)}"

            if (partnerId == myUserId) {
                return Result.failure(Exception("Cannot pair with yourself"))
            }

            val pairingId = listOf(myUserId, partnerId).sorted().joinToString("_")

            val existing = pairingDao.getPairing(pairingId)
            if (existing != null) {
                return Result.failure(Exception("Already paired with this contact"))
            }

            try {
                encryptionManager.decodePreKeyBundleFromQr(preKeyBundleBase64)
            } catch (e: Exception) {
                Log.w("PairingRepository", "Could not decode pre-key bundle: ${e.message}")
            }

            val payload = """{"pairingId":"$pairingId","partnerId":"$partnerId","preKeyBundle":"$preKeyBundleBase64"}"""
            try {
                val wsMessage = WsMessage(type = "COMPLETE_PAIRING", payload = payload)
                webSocketClient.send(wsMessage)
            } catch (e: Exception) {
                Log.w("PairingRepository", "WebSocket send failed: ${e.message}")
            }

            val pairing = Pairing(
                pairingId = pairingId,
                partnerId = partnerId,
                partnerName = partnerName,
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
            Log.e("PairingRepository", "Pairing failed", e)
            Result.failure(e)
        }
    }

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

    suspend fun updatePartnerName(partnerId: String, name: String) {
        pairingDao.updatePartnerNameByPartnerId(partnerId, name)
    }

    suspend fun handlePairingComplete(serverPairingId: String, partnerId: String) {
        val existing = pairingDao.getPairing(serverPairingId)
        if (existing != null) {
            return
        }

        val entity = PairingEntity(
            pairingId = serverPairingId,
            partnerId = partnerId,
            partnerName = "Contact ${partnerId.take(8)}",
            createdAt = System.currentTimeMillis()
        )
        pairingDao.insertPairing(entity)
    }
}
