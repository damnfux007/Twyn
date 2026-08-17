package com.twyn.app.encryption

import java.security.KeyPair
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKey

class TwynSessionStore {
    private val sessions = ConcurrentHashMap<String, SecretKey>()

    fun containsSession(name: String): Boolean = sessions.containsKey(name)

    fun loadSession(name: String): SecretKey? = sessions[name]

    fun storeSession(name: String, key: SecretKey) {
        sessions[name] = key
    }

    fun deleteSession(name: String): Boolean = sessions.remove(name) != null

    fun deleteAllSessions(): Int {
        val count = sessions.size
        sessions.clear()
        return count
    }
}

class TwynPreKeyStore {
    private val preKeys = ConcurrentHashMap<Int, KeyPair>()

    fun containsPreKeyId(preKeyId: Int): Boolean = preKeys.containsKey(preKeyId)

    fun loadPreKey(preKeyId: Int): KeyPair? = preKeys[preKeyId]

    fun storePreKey(preKeyId: Int, keyPair: KeyPair) {
        preKeys[preKeyId] = keyPair
    }

    fun removePreKey(preKeyId: Int): Boolean = preKeys.remove(preKeyId) != null
}

class TwynSignedPreKeyStore {
    private val signedPreKeys = ConcurrentHashMap<Int, KeyPair>()

    fun containsSignedPreKeyId(keyId: Int): Boolean = signedPreKeys.containsKey(keyId)

    fun loadSignedPreKey(keyId: Int): KeyPair? = signedPreKeys[keyId]

    fun loadSignedPreKeys(): List<KeyPair> = signedPreKeys.values.toList()

    fun storeSignedPreKey(keyId: Int, keyPair: KeyPair) {
        signedPreKeys[keyId] = keyPair
    }

    fun removeSignedPreKey(keyId: Int): Boolean = signedPreKeys.remove(keyId) != null
}

class TwynIdentityKeyStore(
    private val keyPair: KeyPair,
    private val localRegistrationId: Int
) {
    private val trustedKeys = ConcurrentHashMap<String, java.security.PublicKey>()

    fun isTrustedIdentity(name: String, publicKey: java.security.PublicKey): Boolean {
        val existing = trustedKeys[name]
        return existing == null || existing == publicKey
    }

    fun getIdentityKeyPair(): KeyPair = keyPair

    fun getLocalRegistrationId(): Int = localRegistrationId

    fun saveIdentity(name: String, publicKey: java.security.PublicKey): Boolean {
        val existing = trustedKeys.put(name, publicKey)
        return existing != null && existing != publicKey
    }

    fun removeIdentity(name: String): Boolean = trustedKeys.remove(name) != null
}
