package com.twyn.app.encryption

import android.content.Context
import android.util.Base64
import com.twyn.app.domain.model.PairingKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import org.signal.libsignal.protocol.*
import org.signal.libsignal.protocol.state.*
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signal Protocol encryption engine for Twyn.
 *
 * Uses libsignal (Signal's own open-source crypto library) for:
 * - Double Ratchet algorithm (forward secrecy)
 * - X3DH key agreement (initial key exchange)
 * - AES-256 encryption of message content
 *
 * Each paired connection gets its own independent Signal Protocol session
 * with unique encryption keys — compromising one pairing does not affect others.
 *
 * Flow:
 * 1. User A generates identity key pair + one-time pre-keys on first launch
 * 2. When pairing via QR, User A's pre-key bundle is shared with User B
 * 3. User B creates a session using X3DH
 * 4. Both users ratchet forward with each message (forward secrecy)
 */
@Singleton
class EncryptionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "twyn_identity"
        private const val KEY_IDENTITY_KEY_PAIR = "identity_key_pair"
        private const val KEY_REGISTRATION_ID = "registration_id"
        private const val KEY_INITIAL_PREKEY_ID = "initial_prekey_id"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    /**
     * Get or create the device's long-term identity key pair.
     * This identity persists across all pairings — it's how others identify this device.
     */
    fun getOrCreateIdentityKeyPair(): IdentityKeyPair {
        val stored = prefs.getString(KEY_IDENTITY_KEY_PAIR, null)
        if (stored != null) {
            return IdentityKeyPair(Base64.decode(stored, Base64.DEFAULT))
        }
        val keyPair = IdentityKeyPair.generateKeyPair(Curve.DJB)
        prefs.edit()
            .putString(KEY_IDENTITY_KEY_PAIR, Base64.encodeToString(keyPair.serialize(), Base64.DEFAULT))
            .apply()
        return keyPair
    }

    /**
     * Get or create the registration ID (unique per device installation).
     */
    fun getOrCreateRegistrationId(): Int {
        val stored = prefs.getInt(KEY_REGISTRATION_ID, -1)
        if (stored != -1) return stored
        val regId = secureRandom.nextInt(16383) + 1 // 14-bit range
        prefs.edit().putInt(KEY_REGISTRATION_ID, regId).apply()
        return regId
    }

    /**
     * Generate a one-time pre-key bundle for QR code pairing.
     * This bundle is what gets encoded into the QR code and shared with the partner.
     *
     * Returns a base64-encoded string containing:
     * - Identity public key
     * - Registration ID
     * - Signed pre-key (public + signature)
     * - One-time pre-key (public)
     */
    fun generatePreKeyBundle(): PreKeyBundle {
        val identityKeyPair = getOrCreateIdentityKeyPair()
        val registrationId = getOrCreateRegistrationId()

        // Generate signed pre-key
        val signedPreKeyPair = Curve.generateKeyPair()
        val signedPreKeySignature = Curve.calculateSignature(
            identityKeyPair.privateKey,
            signedPreKeyPair.publicKey.serialize()
        )
        val signedPreKeyId = 1

        // Generate one-time pre-key
        val preKeyPair = Curve.generateKeyPair()
        val preKeyId = (prefs.getInt(KEY_INITIAL_PREKEY_ID, 0)) + 1
        prefs.edit().putInt(KEY_INITIAL_PREKEY_ID, preKeyId).apply()

        return PreKeyBundle(
            registrationId,
            1, // deviceId
            preKeyId,
            preKeyPair.publicKey,
            signedPreKeyId,
            signedPreKeyPair.publicKey,
            signedPreKeySignature,
            identityKeyPair.identityKey
        )
    }

    /**
     * Create encryption sessions for a new pairing.
     * Called when a pairing is completed via QR code scanning.
     */
    fun createPairingKeys(): PairingKeys {
        val identityKeyPair = getOrCreateIdentityKeyPair()
        val registrationId = getOrCreateRegistrationId()

        return PairingKeys(
            identityKeyPair = identityKeyPair,
            registrationId = registrationId,
            sessionStore = TwynSessionStore(),
            preKeyStore = TwynPreKeyStore(),
            signedPreKeyStore = TwynSignedPreKeyStore(),
            identityKeyStore = TwynIdentityKeyStore(identityKeyPair, registrationId)
        )
    }

    /**
     * Encrypt a plaintext message for a specific pairing.
     * Uses the Signal Protocol session established during pairing.
     *
     * @param sessionStore The session store for this pairing
     * @param plaintext The message text to encrypt
     * @param remoteAddress The partner's Signal Protocol address
     * @return Base64-encoded ciphertext
     */
    fun encryptMessage(
        sessionStore: TwynSessionStore,
        plaintext: ByteArray,
        remoteAddress: SignalProtocolAddress
    ): ByteArray {
        val sessionCipher = SessionCipher(sessionStore, remoteAddress)
        return sessionCipher.encrypt(plaintext).serialize()
    }

    /**
     * Decrypt a received ciphertext message.
     *
     * @param sessionStore The session store for this pairing
     * @param ciphertext The encrypted message bytes
     * @param remoteAddress The sender's Signal Protocol address
     * @return Decrypted plaintext bytes
     */
    fun decryptMessage(
        sessionStore: TwynSessionStore,
        ciphertext: ByteArray,
        remoteAddress: SignalProtocolAddress
    ): ByteArray {
        val cipher = SessionCipher(sessionStore, remoteAddress)
        val message = SignalMessage(ciphertext)
        return cipher.decrypt(message)
    }

    /**
     * Initialize a session from a received pre-key message (first message in pairing).
     */
    fun processPreKeyMessage(
        sessionStore: TwynSessionStore,
        preKeyMessage: PreKeySignalMessage,
        remoteAddress: SignalProtocolAddress
    ) {
        val cipher = SessionCipher(sessionStore, remoteAddress)
        cipher.decrypt(preKeyMessage)
    }

    /**
     * Encode a pre-key bundle as a base64 string for QR code generation.
     */
    fun encodePreKeyBundleForQr(bundle: PreKeyBundle): String {
        return Base64.encodeToString(bundle.serialize(), Base64.NO_WRAP)
    }

    /**
     * Decode a pre-key bundle from a base64 QR code scan.
     */
    fun decodePreKeyBundleFromQr(data: String): PreKeyBundle {
        return PreKeyBundle(Base64.decode(data, Base64.NO_WRAP))
    }
}
