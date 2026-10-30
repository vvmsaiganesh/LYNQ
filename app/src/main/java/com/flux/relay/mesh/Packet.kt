package com.flux.relay.mesh

import com.flux.relay.crypto.CryptoBox
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The unit that moves between devices. Every field except [path] is covered by [sig],
 * so a relay can append itself to the path (for hop accounting and the topology view)
 * without invalidating the author's signature.
 *
 * [ttl] is the hop *budget* and is immutable/signed: a relay forwards only while
 * `path.size < ttl`, which keeps hop limiting tamper-evident.
 */
@Serializable
data class Packet(
    val v: Int = 1,
    val id: String,
    val kind: Kind,
    val src: String,
    val srcName: String = "",
    val srcSignKey: String = "",
    val srcAgreeKey: String = "",
    val dst: String,
    val ts: Long,
    val ttl: Int,
    val prio: Int,
    val enc: Boolean = false,
    val body: String = "",
    val ref: String? = null,
    val reach: List<String> = emptyList(),
    val path: List<String> = emptyList(),
    val sig: String = "",
) {

    enum class Kind { CHAT, SOS, ALERT, ACK, RECEIPT, BEACON }

    val isBroadcast: Boolean get() = dst == BROADCAST

    /** Deterministic bytes the author signs. Deliberately excludes [path] and [sig]. */
    fun signableBytes(): ByteArray = buildString {
        append(v).append('\u001f')
        append(id).append('\u001f')
        append(kind.name).append('\u001f')
        append(src).append('\u001f')
        append(srcSignKey).append('\u001f')
        append(srcAgreeKey).append('\u001f')
        append(dst).append('\u001f')
        append(ts).append('\u001f')
        append(ttl).append('\u001f')
        append(prio).append('\u001f')
        append(if (enc) 1 else 0).append('\u001f')
        append(body).append('\u001f')
        append(ref ?: "").append('\u001f')
        append(reach.joinToString(","))
    }.toByteArray(Charsets.UTF_8)

    /** Relay step: append self to the path. Signature stays valid by design. */
    fun relayedBy(fingerprint: String): Packet =
        copy(path = (path + fingerprint).take(MAX_PATH))

    fun encoded(): ByteArray = json.encodeToString(serializer(), this).toByteArray(Charsets.UTF_8)

    companion object {
        const val BROADCAST = "*"
        const val MAX_PATH = 16
        const val TTL_CHAT = 8
        const val TTL_SOS = 12
        const val TTL_RECEIPT = 6
        const val TTL_BEACON = 1

        const val PRIO_SOS = 0
        const val PRIO_URGENT = 1
        const val PRIO_NORMAL = 2

        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun decode(bytes: ByteArray): Packet? = try {
            json.decodeFromString(serializer(), bytes.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }

        fun b64(bytes: ByteArray): String = CryptoBox.b64(bytes)
    }
}

/** Why a packet was accepted or dropped — surfaced in the debug/diagnostics panel. */
enum class DropReason { MALFORMED, BAD_SIGNATURE, IDENTITY_MISMATCH, REPLAY, EXPIRED, TTL_EXHAUSTED, BLOCKED }
