package com.flux.relay.crypto

import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

/**
 * A device's long-lived cryptographic identity.
 *
 * Two key pairs on purpose:
 *  - [signing]  authenticates packets (ECDSA P-256). Its fingerprint is the node's id.
 *  - [agreement] receives encrypted message bodies (ECDH P-256).
 *
 * Separating them means a relay that learns the signing key cannot decrypt anything.
 */
data class Identity(
    val displayName: String,
    val signing: KeyPair,
    val agreement: KeyPair,
) {
    val fingerprint: String by lazy { CryptoBox.fingerprint(signing.public) }

    val signPublicB64: String get() = CryptoBox.b64(signing.public.encoded)
    val agreePublicB64: String get() = CryptoBox.b64(agreement.public.encoded)

    /** Short, human-comparable code for out-of-band verification (safety numbers). */
    val safetyCode: String by lazy {
        val digest = CryptoBox.sha256(signing.public.encoded + agreement.public.encoded)
        digest.take(4).joinToString("-") { "%03d".format(it.toInt() and 0xFF) }
    }

    fun signPayload(data: ByteArray): ByteArray = CryptoBox.sign(signing.private, data)
}

/** Public half of a peer identity, learned over the mesh and pinned on first sight (TOFU). */
data class PeerIdentity(
    val fingerprint: String,
    val displayName: String,
    val signPublic: PublicKey,
    val agreePublic: PublicKey,
    val signPublicB64: String,
    val agreePublicB64: String,
) {
    companion object {
        fun from(displayName: String, signPublicB64: String, agreePublicB64: String): PeerIdentity? = try {
            val sign = CryptoBox.publicKeyFromB64(signPublicB64)
            val agree = CryptoBox.publicKeyFromB64(agreePublicB64)
            PeerIdentity(
                fingerprint = CryptoBox.fingerprint(sign),
                displayName = displayName,
                signPublic = sign,
                agreePublic = agree,
                signPublicB64 = signPublicB64,
                agreePublicB64 = agreePublicB64,
            )
        } catch (_: Exception) {
            null
        }
    }
}

fun PrivateKey.b64(): String = CryptoBox.b64(encoded)
