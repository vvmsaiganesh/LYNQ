package com.flux.relay.mesh

/**
 * Everything the mesh-graph screen draws. Recomputed on peer/presence changes rather
 * than per frame, so the Canvas can animate freely.
 */
data class Topology(
    val selfId: String = "",
    val selfName: String = "",
    val nodes: List<TopologyNode> = emptyList(),
    val links: List<TopologyLink> = emptyList(),
) {
    val directCount: Int get() = nodes.count { it.hops == 1 }
    val reachableCount: Int get() = nodes.size
}

data class TopologyNode(
    val id: String,
    val label: String,
    val hops: Int,
    val online: Boolean,
    val verified: Boolean = false,
)

data class TopologyLink(val from: String, val to: String)

data class MeshStats(
    val seen: Int = 0,
    val forwarded: Int = 0,
    val received: Int = 0,
    val dropped: Int = 0,
    val queued: Int = 0,
)

/** One entry per directly-connected peer, plus whatever we know about beyond it. */
data class OnlinePeer(
    val fingerprint: String,
    val name: String,
    val endpointId: String,
    val reach: Set<String> = emptySet(),
    val lastHeard: Long = 0L,
)

sealed interface MeshEvent {
    data class PeerJoined(val fingerprint: String, val name: String) : MeshEvent
    data class PeerLeft(val fingerprint: String) : MeshEvent
    data class Received(val message: com.flux.relay.data.Message) : MeshEvent
    data class Delivered(val messageId: String, val hops: Int, val path: List<String>) : MeshEvent
    data class Relayed(val messageId: String, val via: String, val hops: Int) : MeshEvent
    data class Forwarded(val packetId: String, val kind: Packet.Kind, val toPeer: String) : MeshEvent
    data class Dropped(val reason: DropReason) : MeshEvent
}
