package com.flux.relay.transport

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Nearby Connections in P2P_CLUSTER mode: every device both advertises and discovers,
 * and can hold several simultaneous links. That gives the mesh layer a real graph to
 * route over — Bluetooth Classic, BLE and Wi-Fi Direct are all used underneath, with
 * no internet, pairing dialog or server involved.
 *
 * P2P_CLUSTER does not relay anything by itself; multi-hop forwarding is ours
 * (see MeshEngine).
 */
class NearbyTransport(
    context: Context,
    private val serviceId: String,
    private val nickname: String,
    private val scope: CoroutineScope,
) : Transport {

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val strategy = Strategy.P2P_CLUSTER

    private val _peers = MutableStateFlow<List<TransportPeer>>(emptyList())
    override val peers: StateFlow<List<TransportPeer>> = _peers.asStateFlow()

    private val _radio = MutableStateFlow(RadioState.OFF)
    override val radio: StateFlow<RadioState> = _radio.asStateFlow()

    private var listener: Transport.Listener? = null
    private val known = ConcurrentHashMap<String, TransportPeer>()
    private val connecting = ConcurrentHashMap<String, Boolean>()
    private val announcedNames = ConcurrentHashMap<String, String>()

    @Volatile private var started = false
    @Volatile private var lowPower = false
    private var dutyCycle: Job? = null

    private val connectionLifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            announcedNames[endpointId] = info.endpointName
            runCatching { client.acceptConnection(endpointId, payloadCallback) }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            connecting.remove(endpointId)
            if (result.status.isSuccess) {
                val peer = TransportPeer(
                    endpointId = endpointId,
                    displayName = announcedNames.remove(endpointId)?.ifBlank { "peer" } ?: "peer",
                )
                known[endpointId] = peer
                _peers.value = known.values.toList()
                _radio.value = RadioState.CONNECTED
                listener?.onPeerConnected(peer)
            }
        }

        override fun onDisconnected(endpointId: String) {
            known.remove(endpointId)
            connecting.remove(endpointId)
            announcedNames.remove(endpointId)
            _peers.value = known.values.toList()
            listener?.onPeerDisconnected(endpointId)
            if (known.isEmpty() && started) _radio.value = RadioState.DISCOVERING
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                if (bytes.size <= MAX_PAYLOAD_BYTES) listener?.onBytes(endpointId, bytes)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (known.containsKey(endpointId)) return
            if (connecting.putIfAbsent(endpointId, true) != null) return
            client.requestConnection(nickname, endpointId, connectionLifecycle)
                .addOnFailureListener { connecting.remove(endpointId) }
        }

        override fun onEndpointLost(endpointId: String) {
            connecting.remove(endpointId)
        }
    }

    override fun setListener(listener: Transport.Listener?) {
        this.listener = listener
    }

    override suspend fun start() {
        if (started) return
        started = true
        try {
            client.startAdvertising(
                nickname,
                serviceId,
                connectionLifecycle,
                AdvertisingOptions.Builder().setStrategy(strategy).build(),
            ).await()
            startDiscovery()
            _radio.value = RadioState.DISCOVERING
        } catch (t: Throwable) {
            // Missing permission, radios off, or Play Services absent.
            // Logged, not swallowed: "RADIO OFF" on screen is useless without the cause.
            Log.w(TAG, "Nearby start failed: ${t.javaClass.simpleName}: ${t.message}", t)
            _radio.value = RadioState.UNAVAILABLE
            started = false
        }
    }

    private fun startDiscovery() {
        runCatching {
            client.startDiscovery(
                serviceId,
                discoveryCallback,
                DiscoveryOptions.Builder().setStrategy(strategy).build(),
            ).addOnFailureListener { _radio.value = RadioState.UNAVAILABLE }
        }
    }

    override suspend fun send(endpointId: String, bytes: ByteArray): Boolean = try {
        client.sendPayload(endpointId, Payload.fromBytes(bytes)).await()
        true
    } catch (_: Throwable) {
        false
    }

    /**
     * Low-power mode: discovery runs in short bursts instead of continuously. Advertising
     * stays on (it is the cheap direction and keeps us findable), scanning is what costs
     * battery, so scanning gets the duty cycle.
     */
    override fun setLowPower(enabled: Boolean) {
        lowPower = enabled
        dutyCycle?.cancel()
        if (!started) return

        if (!enabled) {
            startDiscovery()
            _radio.value = if (known.isEmpty()) RadioState.DISCOVERING else RadioState.CONNECTED
            return
        }

        dutyCycle = scope.launch {
            while (isActive && lowPower) {
                if (known.isEmpty()) {
                    startDiscovery()
                    _radio.value = RadioState.DISCOVERING
                    delay(SCAN_ON_MS)
                    runCatching { client.stopDiscovery() }
                    _radio.value = RadioState.SLEEPING
                    delay(SCAN_OFF_MS)
                } else {
                    _radio.value = RadioState.CONNECTED
                    delay(SCAN_OFF_MS / 2)
                }
            }
        }
    }

    override fun stop() {
        started = false
        lowPower = false
        dutyCycle?.cancel()
        runCatching { client.stopAllEndpoints() }
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        known.clear()
        connecting.clear()
        _peers.value = emptyList()
        _radio.value = RadioState.OFF
    }

    private companion object {
        const val TAG = "NearbyTransport"
        // Nearby accepts up to 32 KB inline payloads; packets are far smaller.
        const val MAX_PAYLOAD_BYTES = 32 * 1024
        const val SCAN_ON_MS = 8_000L
        const val SCAN_OFF_MS = 42_000L
    }
}
