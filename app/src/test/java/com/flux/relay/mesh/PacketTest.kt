package com.flux.relay.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketTest {

    private fun packet(vararg path: String) = Packet(
        id = "p-1",
        kind = Packet.Kind.CHAT,
        src = "AAAA",
        srcName = "Alice",
        srcSignKey = "sign-key",
        srcAgreeKey = "agree-key",
        dst = "BBBB",
        ts = 1_700_000_000_000,
        ttl = Packet.TTL_CHAT,
        prio = Packet.PRIO_NORMAL,
        enc = true,
        body = "ciphertext",
        reach = listOf("CCCC", "DDDD"),
        path = path.toList(),
    )

    @Test
    fun `signable bytes exclude the mutable hop path and signature`() {
        val base = packet()
        val relayed = base.relayedBy("EEEE").copy(sig = "signature")
        assertEquals("EEEE", relayed.path.single())
        assertArrayEquals(base.signableBytes(), relayed.signableBytes())
    }

    @Test
    fun `every immutable field is signed`() {
        val base = packet().signableBytes()

        assertFalseSameAs(base, packet().copy(ttl = 99).signableBytes())
        assertFalseSameAs(base, packet().copy(dst = "CCCC").signableBytes())
        assertFalseSameAs(base, packet().copy(prio = Packet.PRIO_SOS).signableBytes())
        assertFalseSameAs(base, packet().copy(body = "other").signableBytes())
        assertFalseSameAs(base, packet().copy(enc = false).signableBytes())
        assertFalseSameAs(base, packet().copy(kind = Packet.Kind.SOS).signableBytes())
        assertFalseSameAs(base, packet().copy(ts = 1).signableBytes())
    }

    @Test
    fun `path growth never exceeds the cap`() {
        var p = packet()
        repeat(Packet.MAX_PATH + 5) { p = p.relayedBy("N$it") }
        assertEquals(Packet.MAX_PATH, p.path.size)
    }

    @Test
    fun `wire encoding round-trips`() {
        val original = packet("RELAY1").copy(sig = "sig")
        val decoded = Packet.decode(original.encoded())

        assertNotNull(decoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `garbage does not decode into a packet`() {
        assertNull(Packet.decode("not json at all".toByteArray()))
        assertNull(Packet.decode(byteArrayOf(0, 1, 2, 3)))
    }

    @Test
    fun `broadcast detection`() {
        assertTrue(packet().copy(dst = Packet.BROADCAST).isBroadcast)
        assertTrue(!packet().isBroadcast)
    }

    private fun assertFalseSameAs(a: ByteArray, b: ByteArray) {
        assertTrue("expected signed bytes to differ", !a.contentEquals(b))
    }
}
