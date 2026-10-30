package com.flux.relay.data

/** How a message should be treated by the router and by the UI. */
enum class MessageKind { CHAT, ALERT, SOS;

    companion object {
        fun fromWire(v: String) = entries.firstOrNull { it.name.equals(v, true) } ?: CHAT
    }
}

/**
 * Delivery lifecycle. `PENDING` is local-only (queued until any peer is reachable),
 * `RELAYED` means at least one intermediate node carried it, `DELIVERED` means the
 * recipient's device acknowledged it.
 */
enum class MessageStatus { PENDING, SENT, RELAYED, DELIVERED, FAILED;

    companion object {
        fun fromWire(v: String) = entries.firstOrNull { it.name.equals(v, true) } ?: PENDING
    }
}

data class Message(
    val id: String,
    val peerId: String,
    val peerName: String,
    val body: String,
    val kind: MessageKind,
    val priority: Int,
    val outgoing: Boolean,
    val status: MessageStatus,
    val timestamp: Long,
    val hops: Int = 0,
    val path: List<String> = emptyList(),
    val aiSummary: String? = null,
    val aiUrgency: Int? = null,
    val unread: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /**
     * The signed wire packet, persisted so a queued message can be retransmitted
     * verbatim (same id, same signature) after a reboot or a long radio blackout.
     */
    val wire: String? = null,
) {
    val isBroadcast: Boolean get() = peerId == BROADCAST

    companion object {
        const val BROADCAST = "*"
    }
}
