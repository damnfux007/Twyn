package com.twyn.app.encryption

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "twyn_identity"
        private const val KEY_IDENTITY_KEY = "identity_key"
        private const val KEY_REGISTRATION_ID = "registration_id"
        private const val KEY_INITIAL_PREKEY_ID = "initial_prekey_id"
        private const val AES_KEY_SIZE = 256
        private const val GCM_TAG_LENGTH = 128
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    fun getOrCreateIdentityKeyPair(): KeyPair {
        val stored = prefs.getString(KEY_IDENTITY_KEY, null)
        if (stored != null) {
            val bytes = Base64.decode(stored, Base64.DEFAULT)
            val privKey = java.security.KeyFactory.getInstance("EC")
                .generatePrivate(java.security.spec.PKCS8EncodedKeySpec(bytes))
            val pubKey = java.security.KeyFactory.getInstance("EC")
                .generatePublic(java.security.spec.X509EncodedKeySpec(
                    prefs.getString("identity_public_key", "")?.let { Base64.decode(it, Base64.DEFAULT) } ?: byteArrayOf()
                ))
            return KeyPair(pubKey, privKey)
        }
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        val keyPair = kpg.generateKeyPair()
        prefs.edit()
            .putString(KEY_IDENTITY_KEY, Base64.encodeToString(keyPair.private.encoded, Base64.DEFAULT))
            .putString("identity_public_key", Base64.encodeToString(keyPair.public.encoded, Base64.DEFAULT))
            .apply()
        return keyPair
    }

    fun getOrCreateRegistrationId(): Int {
        val stored = prefs.getInt(KEY_REGISTRATION_ID, -1)
        if (stored != -1) return stored
        val regId = secureRandom.nextInt(16383) + 1
        prefs.edit().putInt(KEY_REGISTRATION_ID, regId).apply()
        return regId
    }

    fun generatePreKeyBundle(): PreKeyBundleData {
        val keyPair = getOrCreateIdentityKeyPair()
        val registrationId = getOrCreateRegistrationId()

        val kg = KeyGenerator.getInstance("AES")
        kg.init(AES_KEY_SIZE, secureRandom)
        val signedPreKey = kg.generateKey()
        val preKey = kg.generateKey()

        val preKeyId = (prefs.getInt(KEY_INITIAL_PREKEY_ID, 0)) + 1
        prefs.edit().putInt(KEY_INITIAL_PREKEY_ID, preKeyId).apply()

        return PreKeyBundleData(
            registrationId = registrationId,
            deviceId = 1,
            preKeyId = preKeyId,
            preKeyPublic = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP),
            signedPreKeyId = 1,
            signedPreKeyPublic = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP),
            signedPreKeySignature = Base64.encodeToString(ByteArray(64).also { secureRandom.nextBytes(it) }, Base64.NO_WRAP),
            identityKeyPublic = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        )
    }

    fun createPairingKeys(): PairingKeys {
        val keyPair = getOrCreateIdentityKeyPair()
        val registrationId = getOrCreateRegistrationId()
        return PairingKeys(
            publicKey = Base64.encodeToString(keyPair.public.encoded, Base64.DEFAULT),
            privateKey = Base64.encodeToString(keyPair.private.encoded, Base64.DEFAULT),
            registrationId = registrationId
        )
    }

    fun encryptMessage(plaintext: ByteArray, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(plaintext)
        return iv + encrypted
    }

    fun decryptMessage(ciphertext: ByteArray, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ciphertext.sliceArray(0..11)
        val encrypted = ciphertext.sliceArray(12 until ciphertext.size)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(encrypted)
    }

    fun generateSecretKey(): SecretKey {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(AES_KEY_SIZE, secureRandom)
        return kg.generateKey()
    }

    fun encodePreKeyBundleForQr(bundle: PreKeyBundleData): String {
        return Base64.encodeToString(
            "$bundle.registrationId:${bundle.preKeyId}:${bundle.preKeyPublic}:${bundle.signedPreKeyId}:${bundle.signedPreKeyPublic}:${bundle.signedPreKeySignature}:${bundle.identityKeyPublic}:${bundle.deviceId}".toByteArray(),
            Base64.NO_WRAP
        )
    }

    fun decodePreKeyBundleFromQr(data: String): PreKeyBundleData {
        val decoded = String(Base64.decode(data, Base64.NO_WRAP))
        val parts = decoded.split(":")
        return PreKeyBundleData(
            registrationId = parts[0].toInt(),
            preKeyId = parts[1].toInt(),
            preKeyPublic = parts[2],
            signedPreKeyId = parts[3].toInt(),
            signedPreKeyPublic = parts[4],
            signedPreKeySignature = parts[5],
            identityKeyPublic = parts[6],
            deviceId = parts.getOrElse(7) { "1" }.toInt()
        )
    }
}

data class PreKeyBundleData(
    val registrationId: Int,
    val deviceId: Int,
    val preKeyId: Int,
    val preKeyPublic: String,
    val signedPreKeyId: Int,
    val signedPreKeyPublic: String,
    val signedPreKeySignature: String,
    val identityKeyPublic: String
)
