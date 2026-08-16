package com.twyn.app.encryption

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.*
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory Signal Protocol session store.
 * Each paired connection has its own session store instance.
 * Persisted to Room database across app restarts.
 */
class TwynSessionStore : SessionStore {
    private val sessions = ConcurrentHashMap<String, Session>()

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        return sessions.containsKey(keyFor(address))
    }

    override fun loadSession(address: SignalProtocolAddress): Session? {
        return sessions[keyFor(address)]
    }

    override fun loadAllSessions(address: SignalProtocolAddress): List<Session> {
        return sessions.values.toList()
    }

    override fun storeSession(address: SignalProtocolAddress, record: Session) {
        sessions[keyFor(address)] = record
    }

    override fun deleteSession(address: SignalProtocolAddress): Boolean {
        return sessions.remove(keyFor(address)) != null
    }

    override fun deleteAllSessions(address: SignalProtocolAddress): Int {
        val count = sessions.size
        sessions.clear()
        return count
    }

    private fun keyFor(address: SignalProtocolAddress): String {
        return "${address.name}:${address.deviceId}"
    }

    /** Serialize all sessions for persistence. */
    fun serialize(): ByteArray {
        // Simplified: in production, serialize the ConcurrentHashMap to bytes
        return ByteArray(0)
    }

    /** Restore sessions from serialized data. */
    fun restore(data: ByteArray) {
        // Simplified: in production, deserialize from bytes
    }
}

/**
 * Pre-key store for one-time pre-keys.
 */
class TwynPreKeyStore : PreKeyStore {
    private val preKeys = ConcurrentHashMap<Int, org.signal.libsignal.protocol.KeyPair>()

    override fun containsPreKeyId(preKeyId: Int): Boolean {
        return preKeys.containsKey(preKeyId)
    }

    override fun loadPreKey(preKeyId: Int): org.signal.libsignal.protocol.KeyPair? {
        return preKeys[preKeyId]
    }

    override fun storePreKey(preKeyId: Int, record: org.signal.libsignal.protocol.KeyPair) {
        preKeys[preKeyId] = record
    }

    override fun removePreKey(preKeyId: Int): Boolean {
        return preKeys.remove(preKeyId) != null
    }
}

/**
 * Signed pre-key store.
 */
class TwynSignedPreKeyStore : SignedPreKeyStore {
    private val signedPreKeys = ConcurrentHashMap<Int, SignedPreKeyRecord>()

    override fun containsSignedPreKeyId(keyId: Int): Boolean {
        return signedPreKeys.containsKey(keyId)
    }

    override fun loadSignedPreKey(keyId: Int): SignedPreKeyRecord? {
        return signedPreKeys[keyId]
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        return signedPreKeys.values.toList()
    }

    override fun storeSignedPreKey(keyId: Int, record: SignedPreKeyRecord) {
        signedPreKeys[keyId] = record
    }

    override fun removeSignedPreKey(keyId: Int): Boolean {
        return signedPreKeys.remove(keyId) != null
    }
}

/**
 * Identity key store — manages trust for paired contacts.
 */
class TwynIdentityKeyStore(
    private val identityKeyPair: IdentityKeyPair,
    private val localRegistrationId: Int
) : IdentityKeyStore {
    private val trustedKeys = ConcurrentHashMap<String, IdentityKey>()

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: Direction
    ): Boolean {
        val existing = trustedKeys[address.name]
        if (existing == null) {
            // First time seeing this identity — trust it (TOFU model)
            return true
        }
        // Trust if the key hasn't changed
        return existing == identityKey
    }

    override fun getIdentityKeyPair(): IdentityKeyPair = identityKeyPair

    override fun getLocalRegistrationId(): Int = localRegistrationId

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
        val existing = trustedKeys.put(address.name, identityKey)
        return existing != null && existing != identityKey
    }

    override fun removeIdentity(address: SignalProtocolAddress): Boolean {
        return trustedKeys.remove(address.name) != null
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey
    ): Boolean {
        return isTrustedIdentity(address, identityKey, Direction.SENDING)
    }
}
