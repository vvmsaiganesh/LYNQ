package com.flux.relay.data

import android.content.ContentValues
import android.content.Context
import com.flux.relay.crypto.PeerIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class KnownPeer(
    val identity: PeerIdentity,
    val lastSeen: Long,
    val hops: Int,
    val verified: Boolean,
    val blocked: Boolean,
)

/**
 * Trust-on-first-use peer directory. First sighting of a fingerprint pins its public
 * keys; a later packet claiming the same id but different keys is rejected by the
 * mesh layer (see MeshEngine), which is what makes "basic authentication" real.
 */
class PeerStore(context: Context) {

    private val db = RelayDb(context.applicationContext)
    private val _peers = MutableStateFlow<List<KnownPeer>>(emptyList())
    val peers: StateFlow<List<KnownPeer>> = _peers.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _peers.value = queryAll()
    }

    suspend fun pin(identity: PeerIdentity, hops: Int) = withContext(Dispatchers.IO) {
        val existing = find(identity.fingerprint)
        if (existing != null && existing.identity.signPublicB64 != identity.signPublicB64) {
            // Key substitution attempt: keep the pinned keys, do not overwrite trust.
            return@withContext
        }
        val values = ContentValues().apply {
            put("fingerprint", identity.fingerprint)
            put("name", identity.displayName)
            put("sign_pub", identity.signPublicB64)
            put("agree_pub", identity.agreePublicB64)
            put("last_seen", System.currentTimeMillis())
            put("hops", hops)
            if (existing == null) {
                put("verified", 0)
                put("blocked", 0)
            }
        }
        db.writableDatabase.insertWithOnConflict(
            "peers",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
        _peers.value = queryAll()
    }

    suspend fun find(fingerprint: String): KnownPeer? = withContext(Dispatchers.IO) {
        db.readableDatabase.rawQuery("SELECT * FROM peers WHERE fingerprint = ?", arrayOf(fingerprint))
            .use { c -> if (c.moveToFirst()) c.toPeer() else null }
    }

    suspend fun setVerified(fingerprint: String, verified: Boolean) = withContext(Dispatchers.IO) {
        db.writableDatabase.update(
            "peers",
            ContentValues().apply { put("verified", if (verified) 1 else 0) },
            "fingerprint = ?",
            arrayOf(fingerprint),
        )
        _peers.value = queryAll()
    }

    suspend fun setBlocked(fingerprint: String, blocked: Boolean) = withContext(Dispatchers.IO) {
        db.writableDatabase.update(
            "peers",
            ContentValues().apply { put("blocked", if (blocked) 1 else 0) },
            "fingerprint = ?",
            arrayOf(fingerprint),
        )
        _peers.value = queryAll()
    }

    private fun queryAll(): List<KnownPeer> =
        db.readableDatabase.rawQuery("SELECT * FROM peers ORDER BY last_seen DESC", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) cursor.toPeer()?.let { add(it) }
            }
        }

    private fun android.database.Cursor.toPeer(): KnownPeer? {
        val identity = PeerIdentity.from(
            displayName = getString(getColumnIndexOrThrow("name")),
            signPublicB64 = getString(getColumnIndexOrThrow("sign_pub")),
            agreePublicB64 = getString(getColumnIndexOrThrow("agree_pub")),
        ) ?: return null
        return KnownPeer(
            identity = identity,
            lastSeen = getLong(getColumnIndexOrThrow("last_seen")),
            hops = getInt(getColumnIndexOrThrow("hops")),
            verified = getInt(getColumnIndexOrThrow("verified")) == 1,
            blocked = getInt(getColumnIndexOrThrow("blocked")) == 1,
        )
    }
}
