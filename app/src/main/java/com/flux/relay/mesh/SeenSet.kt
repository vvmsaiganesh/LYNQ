package com.flux.relay.mesh

/**
 * Bounded LRU of packet ids seen by this node.
 *
 * Two jobs, both essential to a mesh that does not melt:
 *  1. Flood control — a packet is forwarded at most once per node, so loops terminate
 *     even before the TTL budget runs out.
 *  2. Replay protection — an attacker cannot re-inject a captured packet later.
 */
class SeenSet(private val capacity: Int = 4096) {

    private val order = object : LinkedHashMap<String, Long>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > capacity
    }

    fun addIfNew(id: String): Boolean = synchronized(order) {
        if (order.containsKey(id)) {
            // Re-insert so a repeatedly-heard id stays hot and is not evicted early.
            order[id] = System.currentTimeMillis()
            false
        } else {
            order[id] = System.currentTimeMillis()
            true
        }
    }

    fun contains(id: String): Boolean = synchronized(order) { order.containsKey(id) }

    fun size(): Int = synchronized(order) { order.size }

    fun clear() = synchronized(order) { order.clear() }
}
