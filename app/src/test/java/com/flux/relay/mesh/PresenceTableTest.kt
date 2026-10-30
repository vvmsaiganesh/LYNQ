package com.flux.relay.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceTableTest {

    private fun table() = PresenceTable().apply {
        onNeighbourUp("B")
        onNeighbourUp("C")
        updateAdvert("B", setOf("C", "D", "SELF"))
        updateAdvert("C", setOf("B", "E"))
    }

    @Test
    fun `a direct neighbour is its own next hop`() {
        assertEquals(setOf("B"), table().nextHopsFor("B"))
    }

    @Test
    fun `destinations two hops away route through the advertising neighbour`() {
        assertEquals(setOf("B"), table().nextHopsFor("D"))
        assertEquals(setOf("C"), table().nextHopsFor("E"))
    }

    @Test
    fun `a node reachable through two neighbours returns both`() {
        val table = PresenceTable()
        table.onNeighbourUp("B")
        table.onNeighbourUp("C")
        table.updateAdvert("B", setOf("Z"))
        table.updateAdvert("C", setOf("Z"))

        assertEquals(setOf("B", "C"), table.nextHopsFor("Z"))
    }

    @Test
    fun `unknown destination yields no hint so the caller can flood`() {
        assertTrue(table().nextHopsFor("UNKNOWN").isEmpty())
    }

    @Test
    fun `a neighbour never advertises itself`() {
        val table = PresenceTable()
        table.onNeighbourUp("B")
        table.updateAdvert("B", setOf("B", "C"))

        assertFalse("B" in table.advertisedSnapshot().getValue("B"))
        assertTrue("C" in table.reachableSet())
    }

    @Test
    fun `losing a neighbour removes its own hints`() {
        val table = table()
        table.onNeighbourDown("B")

        assertTrue(table.nextHopsFor("D").isEmpty())
        assertFalse(table.isDirect("B"))
        // C may still advertise B until its next beacon, so only B's own hints are gone.
        assertTrue(table.nextHopsFor("E").isNotEmpty())
        assertFalse("D" in table.reachableSet())
    }

    @Test
    fun `reachable set merges neighbours and advertised nodes`() {
        assertEquals(setOf("B", "C", "D", "E", "SELF"), table().reachableSet())
    }

    @Test
    fun `adverts from unknown peers are ignored`() {
        val table = PresenceTable()
        table.updateAdvert("STRANGER", setOf("D"))

        assertTrue(table.nextHopsFor("D").isEmpty())
        assertTrue(table.reachableSet().isEmpty())
    }
}
