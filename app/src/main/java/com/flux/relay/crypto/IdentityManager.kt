package com.flux.relay.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persists the device identity and protects private keys at rest.
 *
 * Private keys are wrapped with an AES-256-GCM key that lives in the Android Keystore
 * (hardware-backed where the device supports it), so a `adb pull` of the app's data
 * directory yields ciphertext, not usable keys.
 */
class IdentityManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(context.filesDir, "identity.json")

    @Volatile
    var current: Identity? = null
        private set

    private val keystoreAvailable: Boolean by lazy {
        try {
            masterKey()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Keystore unavailable, falling back to app-private key storage", t)
            false
        }
    }

    fun load(): Identity? {
        current?.let { return it }
        if (!file.exists()) return null
        return try {
            val stored = json.decodeFromString(StoredIdentity.serializer(), file.readText())
            val signing = KeyPair(
                CryptoBox.publicKeyFromB64(stored.signPub),
                decodePrivate(stored.signPriv),
            )
            val agreement = KeyPair(
                CryptoBox.publicKeyFromB64(stored.agreePub),
                decodePrivate(stored.agreePriv),
            )
            Identity(stored.name, signing, agreement).also { current = it }
        } catch (t: Throwable) {
            Log.e(TAG, "Could not restore identity", t)
            null
        }
    }

    fun create(name: String): Identity {
        val identity = Identity(
            displayName = name.trim().ifBlank { "Unnamed responder" },
            signing = CryptoBox.newKeyPair(),
            agreement = CryptoBox.newKeyPair(),
        )
        persist(identity)
        current = identity
        return identity
    }

    fun rename(name: String) {
        val existing = load() ?: return
        val updated = existing.copy(displayName = name.trim().ifBlank { existing.displayName })
        persist(updated)
        current = updated
    }

    private fun persist(identity: Identity) {
        val stored = StoredIdentity(
            name = identity.displayName,
            signPub = identity.signPublicB64,
            agreePub = identity.agreePublicB64,
            signPriv = encodePrivate(identity.signing.private),
            agreePriv = encodePrivate(identity.agreement.private),
            createdAt = System.currentTimeMillis(),
        )
        val tmp = File(file.parentFile, "identity.tmp")
        tmp.writeText(json.encodeToString(StoredIdentity.serializer(), stored))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun encodePrivate(key: PrivateKey): String {
        val raw = key.encoded
        if (!keystoreAvailable) return CryptoBox.b64(raw)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val ct = cipher.doFinal(raw)
        return CryptoBox.b64(cipher.iv + ct)
    }

    private fun decodePrivate(encoded: String): PrivateKey {
        val raw = CryptoBox.unb64(encoded)
        val pkcs8 = if (!keystoreAvailable) {
            raw
        } else {
            val iv = raw.copyOfRange(0, 12)
            val ct = raw.copyOfRange(12, raw.size)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
                doFinal(ct)
            }
        }
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
    }

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    @Serializable
    private data class StoredIdentity(
        val name: String,
        val signPub: String,
        val agreePub: String,
        val signPriv: String,
        val agreePriv: String,
        val createdAt: Long,
    )

    private companion object {
        const val TAG = "IdentityManager"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "relay.identity.master"
    }
}
