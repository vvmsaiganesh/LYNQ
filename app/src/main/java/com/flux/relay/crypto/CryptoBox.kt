package com.flux.relay.crypto

import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Everything cryptographic, implemented on platform JCA only (no third-party crypto,
 * no native libs) so it works on every API 26+ device and can be unit-tested on the JVM.
 *
 * Primitives:
 *  - ECDSA P-256 / SHA-256  -> message authenticity (who wrote this packet)
 *  - ECDH  P-256 + HKDF     -> per-message content key agreement
 *  - AES-256-GCM            -> message confidentiality + integrity
 *
 * Threat model: relays forward ciphertext and can verify signatures, but cannot read
 * message bodies. Relays *can* see routing metadata (sender, recipient, hop path) —
 * documented deliberately in the README rather than pretended away.
 */
object CryptoBox {

    private const val CURVE = "secp256r1"
    private const val TAG_LEN_BITS = 128
    private const val IV_LEN = 12
    private const val SEAL_VERSION: Byte = 1
    private val rnd = SecureRandom()

    private val b64e: Base64.Encoder = Base64.getEncoder()
    private val b64d: Base64.Decoder = Base64.getDecoder()

    fun b64(bytes: ByteArray): String = b64e.encodeToString(bytes)
    fun unb64(text: String): ByteArray = b64d.decode(text)

    fun newKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec(CURVE), rnd)
        }.generateKeyPair()

    fun publicKeyFromX509(encoded: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))

    fun publicKeyFromB64(text: String): PublicKey = publicKeyFromX509(unb64(text))

    fun sign(privateKey: PrivateKey, data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(data)
            sign()
        }

    fun verify(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean =
        try {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(data)
                verify(signature)
            }
        } catch (_: Exception) {
            false
        }

    /** Stable, human-readable node id: first 12 base32 chars of SHA-256(signing key). */
    fun fingerprint(signingPublicKey: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(signingPublicKey.encoded)
        return base32(digest).take(12)
    }

    private fun base32(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(alphabet[(buffer shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) out.append(alphabet[(buffer shl (5 - bits)) and 0x1F])
        return out.toString()
    }

    /**
     * Sealed envelope layout (so a relay can store and forward it verbatim):
     *   [1B version][4B ephemeral pub length][ephemeral X.509 pub][12B IV][ciphertext||tag]
     */
    fun seal(recipient: PublicKey, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val ephemeral = newKeyPair()
        val key = deriveKey(ephemeral.private, recipient, aad)
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LEN_BITS, iv))
            updateAAD(aad)
        }
        val ct = cipher.doFinal(plaintext)
        val eph = ephemeral.public.encoded
        return ByteBuffer.allocate(1 + 4 + eph.size + IV_LEN + ct.size)
            .put(SEAL_VERSION)
            .putInt(eph.size)
            .put(eph)
            .put(iv)
            .put(ct)
            .array()
    }

    fun open(recipient: PrivateKey, sealed: ByteArray, aad: ByteArray): ByteArray? = try {
        val buf = ByteBuffer.wrap(sealed)
        val version = buf.get()
        require(version == SEAL_VERSION) { "unknown envelope version" }
        val ephLen = buf.getInt()
        require(ephLen in 1..512) { "bad ephemeral key length" }
        val ephBytes = ByteArray(ephLen).also { buf.get(it) }
        val iv = ByteArray(IV_LEN).also { buf.get(it) }
        val ct = ByteArray(buf.remaining()).also { buf.get(it) }
        val key = deriveKey(recipient, publicKeyFromX509(ephBytes), aad)
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LEN_BITS, iv))
            updateAAD(aad)
            doFinal(ct)
        }
    } catch (_: Exception) {
        null
    }

    private fun deriveKey(privateKey: PrivateKey, publicKey: PublicKey, aad: ByteArray): ByteArray {
        val shared = KeyAgreement.getInstance("ECDH").run {
            init(privateKey)
            doPhase(publicKey, true)
            generateSecret()
        }
        // HKDF-SHA256 (extract + expand) with the packet id as `info` for domain separation.
        val prk = hmac(ByteArray(32), shared)
        return expand(prk, aad, 32)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

    private fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val out = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var counter: Byte = 1
        while (pos < length) {
            t = hmac(prk, t + info + byteArrayOf(counter))
            val n = minOf(t.size, length - pos)
            System.arraycopy(t, 0, out, pos, n)
            pos += n
            counter++
        }
        return out
    }

    fun randomNonce(size: Int = 12): ByteArray = ByteArray(size).also { rnd.nextBytes(it) }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
}
