package com.flux.relay.mesh

/**
 * One-hop routing hints, refreshed by BEACON gossip.
 *
 * Every node advertises "these fingerprints are directly reachable from me". A node
 * then forwards a unicast packet toward neighbours that claim to know the destination
 * instead of blind-flooding it to everyone. Flooding remains the fallback when no
 * hint exists, so delivery is never worse than plain gossip — it is just usually
 * much cheaper on battery and airtime.
 */
class PresenceTable {

    /** neighbour fingerprint -> fingerprints that neighbour says it can reach. */
    private val advertised = mutableMapOf<String, Set<String>>()

    /** fingerprints with a live direct link right now. */
    private val neighbours = mutableSetOf<String>()

    fun onNeighbourUp(fingerprint: String) {
        synchronized(this) { neighbours += fingerprint }
    }

    fun onNeighbourDown(fingerprint: String) {
        synchronized(this) {
            neighbours -= fingerprint
            advertised -= fingerprint
        }
    }

    fun updateAdvert(fingerprint: String, reach: Set<String>) {
        synchronized(this) {
            if (fingerprint in neighbours) {
                advertised[fingerprint] = reach - fingerprint
            }
        }
    }

    fun isDirect(fingerprint: String): Boolean = synchronized(this) { fingerprint in neighbours }

    /** Every fingerprint we believe is reachable within one relay hop. */
    fun reachableSet(): Set<String> = synchronized(this) {
        buildSet {
            addAll(neighbours)
            advertised.values.forEach { addAll(it) }
        }
    }

    /**
     * Neighbours to hand a unicast packet destined for [dst]:
     * the destination itself if direct, otherwise whoever advertises it.
     * Empty list means "no hint" -> caller falls back to flooding.
     */
    fun nextHopsFor(dst: String): Set<String> = synchronized(this) {
        if (dst in neighbours) return@synchronized setOf(dst)
        advertised.filterValues { dst in it }.keys
    }

    fun neighboursSnapshot(): Set<String> = synchronized(this) { neighbours.toSet() }

    fun advertisedSnapshot(): Map<String, Set<String>> = synchronized(this) { advertised.toMap() }

    fun clear() = synchronized(this) {
        neighbours.clear()
        advertised.clear()
    }
}
