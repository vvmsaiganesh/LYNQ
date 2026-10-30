package com.flux.relay.mesh

import com.flux.relay.crypto.CryptoBox
import com.flux.relay.crypto.Identity
import com.flux.relay.crypto.PeerIdentity
import com.flux.relay.data.Message
import com.flux.relay.data.MessageKind
import com.flux.relay.data.MessageStatus
import com.flux.relay.data.MessageStore
import com.flux.relay.data.PeerStore
import com.flux.relay.transport.RadioState
import com.flux.relay.transport.Transport
import com.flux.relay.transport.TransportPeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Collections
import java.util.PriorityQueue
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Plaintext of a message, sealed with AES-GCM before it ever hits the wire (1:1 only). */
@Serializable
data class MessageBody(
    val text: String,
    val name: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val kind: String = MessageKind.CHAT.name,
    val sentAt: Long = 0L,
)

/**
 * The mesh: verification, de-duplication, store-and-forward, priority queueing and
 * presence-aware routing. Deliberately independent of the radio (see [Transport]) and
 * of Android (it takes a [CoroutineScope] and a clock), which is what makes the routing
 * logic unit-testable on the JVM.
 */
class MeshEngine(
    private val identity: Identity,
    private val transport: Transport,
    private val messages: MessageStore,
    private val peers: PeerStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : Transport.Listener {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val seen = SeenSet()
    private val presence = PresenceTable()

    private val endpointToFingerprint = ConcurrentHashMap<String, String>()
    private val fingerprintToEndpoint = ConcurrentHashMap<String, String>()
    private val blocked: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    private val outbound = PriorityQueue<Queued>()
    private val outboundLock = Mutex()
    private val signal = Channel<Unit>(Channel.CONFLATED)

    private val _online = MutableStateFlow<List<OnlinePeer>>(emptyList())
    val online: StateFlow<List<OnlinePeer>> = _online.asStateFlow()

    private val _topology = MutableStateFlow(Topology(identity.fingerprint, identity.displayName))
    val topology: StateFlow<Topology> = _topology.asStateFlow()

    private val _stats = MutableStateFlow(MeshStats(queued = 0))
    val stats: StateFlow<MeshStats> = _stats.asStateFlow()

    private val _events = MutableSharedFlow<MeshEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<MeshEvent> = _events.asSharedFlow()

    val radio: StateFlow<RadioState> get() = transport.radio

    @Volatile private var running = false
    @Volatile private var lowPower = false

    // ---------------------------------------------------------------- lifecycle

    fun start() {
        if (running) return
        running = true
        transport.setListener(this)
        scope.launch { transport.start() }
        scope.launch {
            peers.peers.collect { all ->
                blocked.clear()
                all.filter { it.blocked }.forEach { blocked.add(it.identity.fingerprint) }
            }
        }
        scope.launch { drainLoop() }
        scope.launch { beaconLoop() }
        scope.launch { housekeepingLoop() }
        scope.launch { restoreOutbox() }
    }

    fun stop() {
        running = false
        transport.setListener(null)
        transport.stop()
        endpointToFingerprint.clear()
        fingerprintToEndpoint.clear()
        presence.clear()
        _online.value = emptyList()
        rebuildTopology()
    }

    fun setLowPower(enabled: Boolean) {
        lowPower = enabled
        transport.setLowPower(enabled)
    }

    // ---------------------------------------------------------------- radio callbacks

    override fun onPeerConnected(peer: TransportPeer) {
        scope.launch {
            if (_online.value.none { it.endpointId == peer.endpointId }) {
                _online.update {
                    it + OnlinePeer(
                        fingerprint = peer.endpointId,
                        name = peer.displayName.ifBlank { "peer" },
                        endpointId = peer.endpointId,
                        lastHeard = clock(),
                    )
                }
                rebuildTopology()
            }
            // New link: announce ourselves, then flush anything that was waiting.
            sendBeacon()
            restoreOutbox()
        }
    }

    override fun onPeerDisconnected(endpointId: String) {
        val fingerprint = endpointToFingerprint.remove(endpointId)
        if (fingerprint != null) {
            fingerprintToEndpoint.remove(fingerprint)
            presence.onNeighbourDown(fingerprint)
            _events.tryEmit(MeshEvent.PeerLeft(fingerprint))
        }
        _online.update { list ->
            list.filterNot { it.endpointId == endpointId || it.fingerprint == endpointId }
        }
        rebuildTopology()
    }

    override fun onBytes(endpointId: String, bytes: ByteArray) {
        scope.launch { handlePacket(endpointId, bytes) }
    }

    // ---------------------------------------------------------------- receive path

    private suspend fun handlePacket(endpointId: String, bytes: ByteArray) {
        val packet = Packet.decode(bytes) ?: return drop(DropReason.MALFORMED)
        if (packet.src == identity.fingerprint) return // our own packet echoed back by a peer

        val signPublic = runCatching { CryptoBox.publicKeyFromB64(packet.srcSignKey) }.getOrNull()
            ?: return drop(DropReason.MALFORMED)
        if (CryptoBox.fingerprint(signPublic) != packet.src) return drop(DropReason.IDENTITY_MISMATCH)

        val signature = runCatching { CryptoBox.unb64(packet.sig) }.getOrNull()
            ?: return drop(DropReason.BAD_SIGNATURE)
        if (!CryptoBox.verify(signPublic, packet.signableBytes(), signature)) {
            return drop(DropReason.BAD_SIGNATURE)
        }
        if (abs(clock() - packet.ts) > MAX_CLOCK_SKEW_MS) return drop(DropReason.EXPIRED)
        if (packet.src in blocked) return drop(DropReason.BLOCKED)
        if (!seen.addIfNew(packet.id)) return // already handled: loop or replay
        _stats.update { it.copy(seen = seen.size()) }

        when (packet.kind) {
            Packet.Kind.BEACON -> onBeacon(packet, endpointId)
            Packet.Kind.ACK -> if (packet.dst == identity.fingerprint) {
                onAck(packet)
            } else {
                forwardControl(packet, endpointId)
            }
            Packet.Kind.RECEIPT -> if (packet.dst == identity.fingerprint) {
                onReceipt(packet)
            } else {
                forwardControl(packet, endpointId)
            }
            Packet.Kind.CHAT, Packet.Kind.SOS, Packet.Kind.ALERT -> onContent(packet, endpointId)
        }
    }

    /**
     * ACKs and receipts are routed exactly like content, but they never generate further
     * receipts (which would be an infinite chorus). A relay that cannot reach the author
     * directly hands the packet on; the signature stays valid because only [Packet.path]
     * changes.
     */
    private suspend fun forwardControl(packet: Packet, fromEndpoint: String) {
        if (packet.path.size >= packet.ttl) {
            drop(DropReason.TTL_EXHAUSTED)
            return
        }
        enqueue(packet.relayedBy(identity.fingerprint), excludeEndpoint = fromEndpoint)
        _stats.update { it.copy(forwarded = it.forwarded + 1) }
    }

    private suspend fun onBeacon(packet: Packet, endpointId: String) {
        val peerIdentity = PeerIdentity.from(packet.srcName, packet.srcSignKey, packet.srcAgreeKey)
            ?: return drop(DropReason.MALFORMED)

        // Trust-on-first-use: a fingerprint with different keys is a substitution attempt.
        val pinned = peers.find(packet.src)
        if (pinned != null && pinned.identity.signPublicB64 != packet.srcSignKey) {
            return drop(DropReason.IDENTITY_MISMATCH)
        }
        if (pinned?.blocked == true) return drop(DropReason.BLOCKED)

        peers.pin(peerIdentity, hops = 1)
        endpointToFingerprint[endpointId] = packet.src
        fingerprintToEndpoint[packet.src] = endpointId
        presence.onNeighbourUp(packet.src)
        presence.updateAdvert(packet.src, packet.reach.toSet())

        _online.update { list ->
            list.filterNot { it.endpointId == endpointId || it.fingerprint == packet.src } +
                OnlinePeer(
                    fingerprint = packet.src,
                    name = peerIdentity.displayName.ifBlank { packet.src.take(4) },
                    endpointId = endpointId,
                    reach = packet.reach.toSet(),
                    lastHeard = clock(),
                )
        }
        rebuildTopology()
        _events.tryEmit(MeshEvent.PeerJoined(packet.src, peerIdentity.displayName))
    }

    private suspend fun onContent(packet: Packet, fromEndpoint: String) {
        val forMe = packet.dst == identity.fingerprint
        if (forMe || packet.isBroadcast) deliver(packet)

        val stillNeedsToTravel = packet.isBroadcast || !forMe
        if (stillNeedsToTravel && packet.path.size < packet.ttl) {
            forward(packet, fromEndpoint)
        } else if (stillNeedsToTravel) {
            drop(DropReason.TTL_EXHAUSTED)
        }
    }

    private suspend fun deliver(packet: Packet) {
        val body = decodeBody(packet) ?: return
        val message = Message(
            id = packet.id,
            peerId = if (packet.isBroadcast) Message.BROADCAST else packet.src,
            peerName = if (packet.isBroadcast) "Nearby broadcast"
            else packet.srcName.ifBlank { body.name.ifBlank { packet.src.take(6) } },
            body = body.text,
            kind = when (packet.kind) {
                Packet.Kind.SOS -> MessageKind.SOS
                Packet.Kind.ALERT -> MessageKind.ALERT
                else -> MessageKind.CHAT
            },
            priority = packet.prio,
            outgoing = false,
            status = MessageStatus.DELIVERED,
            timestamp = packet.ts,
            hops = packet.path.size,
            path = packet.path,
            unread = true,
            latitude = body.lat,
            longitude = body.lon,
        )
        messages.insert(message)
        _stats.update { it.copy(received = it.received + 1) }
        _events.tryEmit(MeshEvent.Received(message))

        // Unicast settles with an ACK from the addressee. A broadcast has no single
        // addressee, so every recipient sends a receipt instead — the author still sees
        // RELAYED with a real hop count as the alert spreads.
        if (!packet.isBroadcast) {
            sendAck(packet)
        } else {
            sendReceipt(packet)
        }
    }

    private fun decodeBody(packet: Packet): MessageBody? = try {
        val raw = if (packet.enc) {
            // Only the addressed device holds the agreement key; relays cannot open this.
            if (packet.dst != identity.fingerprint) return null
            CryptoBox.open(
                identity.agreement.private,
                CryptoBox.unb64(packet.body),
                packet.id.toByteArray(Charsets.UTF_8),
            ) ?: return null
        } else {
            CryptoBox.unb64(packet.body)
        }
        json.decodeFromString(MessageBody.serializer(), raw.toString(Charsets.UTF_8))
    } catch (_: Exception) {
        null
    }

    private suspend fun onAck(packet: Packet) {
        val ref = packet.ref ?: return
        val hops = packet.path.size
        val current = messages.messages.value.firstOrNull { it.id == ref }
        if (current != null && current.status == MessageStatus.DELIVERED) return
        messages.updateStatus(ref, MessageStatus.DELIVERED, hops, packet.path)
        _events.tryEmit(MeshEvent.Delivered(ref, hops, packet.path))
    }

    private suspend fun onReceipt(packet: Packet) {
        val ref = packet.ref ?: return
        val current = messages.messages.value.firstOrNull { it.id == ref } ?: return
        if (current.status == MessageStatus.DELIVERED) return
        val hops = packet.path.size
        messages.updateStatus(ref, MessageStatus.RELAYED, hops, packet.path)
        _events.tryEmit(MeshEvent.Relayed(ref, packet.srcName.ifBlank { packet.src.take(6) }, hops))
    }

    // ---------------------------------------------------------------- send path

    /** Sends to one peer (E2E sealed) or, when [to] is null, broadcasts in the clear. */
    suspend fun sendText(
        to: PeerIdentity?,
        text: String,
        kind: MessageKind = MessageKind.CHAT,
        location: Pair<Double, Double>? = null,
    ): String {
        val packetId = UUID.randomUUID().toString()
        val body = MessageBody(
            text = text.trim(),
            name = identity.displayName,
            lat = location?.first,
            lon = location?.second,
            kind = kind.name,
            sentAt = clock(),
        )
        val raw = json.encodeToString(MessageBody.serializer(), body).toByteArray(Charsets.UTF_8)

        val encrypted = to != null
        val wireBody = if (to != null) {
            CryptoBox.b64(CryptoBox.seal(to.agreePublic, raw, packetId.toByteArray(Charsets.UTF_8)))
        } else {
            CryptoBox.b64(raw)
        }

        val priority = when (kind) {
            MessageKind.SOS -> Packet.PRIO_SOS
            MessageKind.ALERT -> Packet.PRIO_URGENT
            MessageKind.CHAT -> Packet.PRIO_NORMAL
        }
        val packet = Packet(
            id = packetId,
            kind = when (kind) {
                MessageKind.SOS -> Packet.Kind.SOS
                MessageKind.ALERT -> Packet.Kind.ALERT
                MessageKind.CHAT -> Packet.Kind.CHAT
            },
            src = identity.fingerprint,
            srcName = identity.displayName,
            srcSignKey = identity.signPublicB64,
            srcAgreeKey = identity.agreePublicB64,
            dst = to?.fingerprint ?: Packet.BROADCAST,
            ts = clock(),
            ttl = if (kind == MessageKind.SOS) Packet.TTL_SOS else Packet.TTL_CHAT,
            prio = priority,
            enc = encrypted,
            body = wireBody,
        ).signed()

        messages.insert(
            Message(
                id = packetId,
                peerId = to?.fingerprint ?: Message.BROADCAST,
                peerName = to?.displayName ?: "Nearby broadcast",
                body = text.trim(),
                kind = kind,
                priority = priority,
                outgoing = true,
                status = MessageStatus.PENDING,
                timestamp = packet.ts,
                latitude = location?.first,
                longitude = location?.second,
                wire = packet.encoded().toString(Charsets.UTF_8),
            ),
        )
        enqueue(packet)
        return packetId
    }

    private suspend fun sendAck(original: Packet) {
        val ack = Packet(
            id = UUID.randomUUID().toString(),
            kind = Packet.Kind.ACK,
            src = identity.fingerprint,
            srcName = identity.displayName,
            srcSignKey = identity.signPublicB64,
            srcAgreeKey = identity.agreePublicB64,
            dst = original.src,
            ts = clock(),
            ttl = Packet.TTL_RECEIPT,
            prio = Packet.PRIO_URGENT,
            ref = original.id,
            path = (original.path + identity.fingerprint).take(Packet.MAX_PATH),
        ).signed()
        enqueue(ack)
    }

    /** Tells the author "this message passed through me" — this is what lights up hops in the UI. */
    private suspend fun sendReceipt(original: Packet) {
        if (original.src == identity.fingerprint) return
        val receipt = Packet(
            id = UUID.randomUUID().toString(),
            kind = Packet.Kind.RECEIPT,
            src = identity.fingerprint,
            srcName = identity.displayName,
            srcSignKey = identity.signPublicB64,
            srcAgreeKey = identity.agreePublicB64,
            dst = original.src,
            ts = clock(),
            ttl = Packet.TTL_RECEIPT,
            prio = if (original.prio == Packet.PRIO_SOS) Packet.PRIO_URGENT else Packet.PRIO_NORMAL,
            ref = original.id,
            path = (original.path + identity.fingerprint).take(Packet.MAX_PATH),
        ).signed()
        enqueue(receipt)
    }

    private suspend fun forward(packet: Packet, fromEndpoint: String) {
        val relayed = packet.relayedBy(identity.fingerprint)
        enqueue(relayed, excludeEndpoint = fromEndpoint)
        _stats.update { it.copy(forwarded = it.forwarded + 1) }
        if (packet.kind == Packet.Kind.CHAT || packet.kind == Packet.Kind.SOS || packet.kind == Packet.Kind.ALERT) {
            sendReceipt(packet)
        }
    }

    private suspend fun sendBeacon() {
        val reach = presence.reachableSet()
            .filter { it != identity.fingerprint }
            .take(MAX_ADVERTISED)
        val beacon = Packet(
            id = UUID.randomUUID().toString(),
            kind = Packet.Kind.BEACON,
            src = identity.fingerprint,
            srcName = identity.displayName,
            srcSignKey = identity.signPublicB64,
            srcAgreeKey = identity.agreePublicB64,
            dst = Packet.BROADCAST,
            ts = clock(),
            ttl = Packet.TTL_BEACON,
            prio = Packet.PRIO_NORMAL,
            reach = reach,
        ).signed()
        enqueue(beacon)
    }

    // ---------------------------------------------------------------- queue

    private suspend fun enqueue(packet: Packet, excludeEndpoint: String? = null) {
        outboundLock.withLock {
            outbound.add(Queued(packet, excludeEndpoint, clock()))
        }
        _stats.update { it.copy(queued = outbound.size) }
        signal.trySend(Unit)
    }

    private suspend fun drainLoop() {
        while (running) {
            signal.receive()
            while (running) {
                val item = outboundLock.withLock { outbound.poll() } ?: break
                route(item)
                _stats.update { it.copy(queued = outbound.size) }
                delay(PACE_MS)
            }
        }
    }

    private suspend fun route(item: Queued): Boolean {
        val targets = resolveTargets(item)
        if (targets.isEmpty()) return false

        val bytes = item.packet.encoded()
        var handedOff = false
        for (endpoint in targets) {
            if (runCatching { transport.send(endpoint, bytes) }.getOrDefault(false)) {
                handedOff = true
                _events.tryEmit(
                    MeshEvent.Forwarded(
                        item.packet.id,
                        item.packet.kind,
                        endpointToFingerprint[endpoint] ?: endpoint,
                    ),
                )
            }
        }
        if (handedOff && item.packet.src == identity.fingerprint) {
            val current = messages.messages.value.firstOrNull { it.id == item.packet.id }
            if (current != null && current.status == MessageStatus.PENDING) {
                messages.updateStatus(item.packet.id, MessageStatus.SENT, current.hops, current.path)
            }
        }
        return handedOff
    }

    private fun resolveTargets(item: Queued): List<String> {
        val connected = transport.peers.value.map { it.endpointId }
        val candidates = connected.filter { it != item.excludeEndpoint }
        val dst = item.packet.dst

        if (dst == Packet.BROADCAST) return candidates
        if (dst == identity.fingerprint) return emptyList()

        val hinted = presence.nextHopsFor(dst)
            .mapNotNull { fingerprintToEndpoint[it] }
            .filter { it != item.excludeEndpoint }
        return if (hinted.isNotEmpty()) hinted else candidates
    }

    /** Re-queues everything still undelivered; called on start and whenever a peer appears. */
    private suspend fun restoreOutbox() {
        val pending = messages.outbox()
        for (message in pending) {
            val wire = message.wire ?: continue
            val packet = Packet.decode(wire.toByteArray(Charsets.UTF_8)) ?: continue
            enqueue(packet)
        }
    }

    private suspend fun beaconLoop() {
        while (running) {
            runCatching { sendBeacon() }
            delay(if (lowPower) BEACON_LOW_POWER_MS else BEACON_MS)
        }
    }

    private suspend fun housekeepingLoop() {
        while (running) {
            delay(HOUSEKEEPING_MS)
            if (!running) break
            val now = clock()
            val stale = _online.value.filter { now - it.lastHeard > PEER_TIMEOUT_MS }
            if (stale.isNotEmpty()) {
                _online.update { list -> list.filterNot { it in stale } }
                stale.forEach { presence.onNeighbourDown(it.fingerprint) }
                rebuildTopology()
            }
            // A message still pending after a long silence is retried on the next peer sighting,
            // but a periodic nudge covers peers that are already connected.
            if (transport.peers.value.isNotEmpty() && messages.outbox().isNotEmpty()) {
                restoreOutbox()
            }
        }
    }

    // ---------------------------------------------------------------- topology

    private fun rebuildTopology() {
        val known = peers.peers.value.associateBy { it.identity.fingerprint }
        val nodes = linkedMapOf(
            identity.fingerprint to TopologyNode(
                id = identity.fingerprint,
                label = identity.displayName,
                hops = 0,
                online = true,
            ),
        )
        val links = mutableListOf<TopologyLink>()

        for (peer in _online.value) {
            if (peer.fingerprint == identity.fingerprint) continue
            nodes[peer.fingerprint] = TopologyNode(
                id = peer.fingerprint,
                label = peer.name.ifBlank { known[peer.fingerprint]?.identity?.displayName ?: peer.fingerprint.take(4) },
                hops = 1,
                online = true,
                verified = known[peer.fingerprint]?.verified == true,
            )
            links += TopologyLink(identity.fingerprint, peer.fingerprint)

            for (reachable in peer.reach) {
                if (reachable == identity.fingerprint || reachable == peer.fingerprint) continue
                if (nodes[reachable] == null) {
                    nodes[reachable] = TopologyNode(
                        id = reachable,
                        label = known[reachable]?.identity?.displayName ?: reachable.take(4),
                        hops = 2,
                        online = false,
                        verified = known[reachable]?.verified == true,
                    )
                }
                links += TopologyLink(peer.fingerprint, reachable)
            }
        }

        _topology.value = Topology(
            selfId = identity.fingerprint,
            selfName = identity.displayName,
            nodes = nodes.values.toList(),
            links = links.distinct(),
        )
    }

    private fun drop(reason: DropReason) {
        _stats.update { it.copy(dropped = it.dropped + 1) }
        _events.tryEmit(MeshEvent.Dropped(reason))
    }

    private fun Packet.signed(): Packet = copy(sig = CryptoBox.b64(identity.signPayload(signableBytes())))

    private data class Queued(
        val packet: Packet,
        val excludeEndpoint: String?,
        val createdAt: Long,
    ) : Comparable<Queued> {
        override fun compareTo(other: Queued): Int {
            val byPriority = packet.prio.compareTo(other.packet.prio)
            return if (byPriority != 0) byPriority else createdAt.compareTo(other.createdAt)
        }
    }

    private companion object {
        const val MAX_CLOCK_SKEW_MS = 2 * 60 * 60 * 1000L // 2h: clocks drift hard in a blackout
        const val BEACON_MS = 15_000L
        const val BEACON_LOW_POWER_MS = 60_000L
        const val HOUSEKEEPING_MS = 20_000L
        const val PEER_TIMEOUT_MS = 90_000L
        const val PACE_MS = 90L
        const val MAX_ADVERTISED = 24
    }
}
