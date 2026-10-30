package com.flux.relay.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenSetTest {

    @Test
    fun `first sighting wins, repeats are rejected`() {
        val seen = SeenSet(capacity = 8)

        assertTrue(seen.addIfNew("a"))
        assertFalse(seen.addIfNew("a"))
        assertTrue(seen.contains("a"))
        assertFalse(seen.contains("b"))
        assertEquals(1, seen.size())
    }

    @Test
    fun `oldest entries are evicted once capacity is reached`() {
        val seen = SeenSet(capacity = 3)
        listOf("a", "b", "c").forEach { assertTrue(seen.addIfNew(it)) }
        assertTrue(seen.addIfNew("d"))

        assertEquals(3, seen.size())
        assertFalse(seen.contains("a"))
        assertTrue(seen.contains("d"))
    }

    @Test
    fun `recently used entries survive eviction`() {
        val seen = SeenSet(capacity = 3)
        listOf("a", "b", "c").forEach { seen.addIfNew(it) }

        // Touch "a" so it becomes most-recently-used, then insert a fourth id.
        assertFalse(seen.addIfNew("a"))
        seen.addIfNew("d")

        assertTrue(seen.contains("a"))
        assertFalse(seen.contains("b"))
    }

    @Test
    fun `clear forgets everything`() {
        val seen = SeenSet()
        seen.addIfNew("x")
        seen.clear()
        assertEquals(0, seen.size())
        assertTrue(seen.addIfNew("x"))
    }
}
