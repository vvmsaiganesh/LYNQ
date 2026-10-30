package com.flux.relay.transport

import kotlinx.coroutines.flow.StateFlow

data class TransportPeer(val endpointId: String, val displayName: String)

/** What the radio is doing right now — surfaced in the UI as the "discovery" indicator. */
enum class RadioState { OFF, ADVERTISING, DISCOVERING, CONNECTED, SLEEPING, UNAVAILABLE }

/**
 * Radio abstraction. Today the only implementation is Nearby Connections
 * (Bluetooth Classic/LE + Wi-Fi Direct underneath), but the mesh layer only ever
 * sees this interface, so a raw Wi-Fi Direct or BLE-GATT transport can be swapped in
 * without touching the protocol.
 */
interface Transport {

    val peers: StateFlow<List<TransportPeer>>
    val radio: StateFlow<RadioState>

    fun setListener(listener: Listener?)

    suspend fun start()

    /** Fire-and-forget send of one encoded packet to one endpoint. */
    suspend fun send(endpointId: String, bytes: ByteArray): Boolean

    /** Battery mode: sparse discovery bursts instead of continuous scanning. */
    fun setLowPower(enabled: Boolean)

    fun stop()

    interface Listener {
        fun onPeerConnected(peer: TransportPeer)
        fun onPeerDisconnected(endpointId: String)
        fun onBytes(endpointId: String, bytes: ByteArray)
    }
}
