package com.flux.relay.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoBoxTest {

    private val alice = CryptoBox.newKeyPair()
    private val bob = CryptoBox.newKeyPair()
    private val aad = "packet-id-123".toByteArray()

    @Test
    fun `sealed body opens only with the addressed key`() {
        val plaintext = """{"text":"need water","lat":17.38,"lon":78.48}""".toByteArray()

        val sealed = CryptoBox.seal(bob.public, plaintext, aad)
        val opened = CryptoBox.open(bob.private, sealed, aad)

        assertNotNull(opened)
        assertArrayEquals(plaintext, opened)
    }

    @Test
    fun `a relay holding the wrong key cannot open the body`() {
        val sealed = CryptoBox.seal(bob.public, "secret".toByteArray(), aad)
        assertNull(CryptoBox.open(alice.private, sealed, aad))
    }

    @Test
    fun `associated data binds the packet id`() {
        val sealed = CryptoBox.seal(bob.public, "secret".toByteArray(), aad)
        // Replaying the ciphertext under a different packet id must fail authentication.
        assertNull(CryptoBox.open(bob.private, sealed, "other-packet-id".toByteArray()))
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val sealed = CryptoBox.seal(bob.public, "secret".toByteArray(), aad)
        sealed[sealed.lastIndex] = (sealed.last().toInt() xor 0xFF).toByte()
        assertNull(CryptoBox.open(bob.private, sealed, aad))
    }

    @Test
    fun `signatures verify and detect modification`() {
        val payload = "REL|1|hello".toByteArray()
        val signature = CryptoBox.sign(alice.private, payload)

        assertTrue(CryptoBox.verify(alice.public, payload, signature))
        assertFalse(CryptoBox.verify(alice.public, "REL|1|hell0".toByteArray(), signature))
        assertFalse(CryptoBox.verify(bob.public, payload, signature))
    }

    @Test
    fun `fingerprint is stable and key-shaped`() {
        val first = CryptoBox.fingerprint(alice.public)
        val second = CryptoBox.fingerprint(CryptoBox.publicKeyFromX509(alice.public.encoded))

        assertEquals(first, second)
        assertEquals(12, first.length)
        assertTrue(first.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" })
    }

    @Test
    fun `key encodings round-trip through base64`() {
        val text = CryptoBox.b64(bob.public.encoded)
        assertArrayEquals(bob.public.encoded, CryptoBox.publicKeyFromB64(text).encoded)
    }
}
