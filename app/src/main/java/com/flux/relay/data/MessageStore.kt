package com.flux.relay.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Source of truth for message state. In-memory [StateFlow] for the UI, SQLite for
 * durability, so a message queued in a blackout survives a process death.
 */
class MessageStore(context: Context) {

    private val db = RelayDb(context.applicationContext)
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _messages.value = queryAll()
    }

    suspend fun insert(message: Message) = withContext(Dispatchers.IO) {
        db.writableDatabase.insertWithOnConflict(
            "messages",
            null,
            message.toValues(),
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
        _messages.value = queryAll()
    }

    suspend fun updateStatus(
        id: String,
        status: MessageStatus,
        hops: Int = 0,
        path: List<String> = emptyList(),
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("status", status.name)
            if (hops > 0) put("hops", hops)
            if (path.isNotEmpty()) put("path", path.joinToString(","))
        }
        db.writableDatabase.update("messages", values, "id = ?", arrayOf(id))
        _messages.value = queryAll()
    }

    suspend fun setAiFields(id: String, summary: String?, urgency: Int?) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("ai_summary", summary)
            if (urgency != null) put("ai_urgency", urgency)
        }
        db.writableDatabase.update("messages", values, "id = ?", arrayOf(id))
        _messages.value = queryAll()
    }

    suspend fun markConversationRead(peerId: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.update(
            "messages",
            ContentValues().apply { put("unread", 0) },
            "peer = ? AND unread = 1",
            arrayOf(peerId),
        )
        _messages.value = queryAll()
    }

    /** Oldest-first queue of unsent/undelivered outgoing messages, ordered by priority. */
    suspend fun outbox(): List<Message> = withContext(Dispatchers.IO) {
        query(
            "SELECT * FROM messages WHERE outgoing = 1 AND status IN ('PENDING','SENT','RELAYED') " +
                "ORDER BY priority ASC, ts ASC",
        )
    }

    suspend fun pendingFor(peerId: String): List<Message> = withContext(Dispatchers.IO) {
        query("SELECT * FROM messages WHERE outgoing = 1 AND peer = ? AND status = 'PENDING'", arrayOf(peerId))
    }

    private fun queryAll(): List<Message> = query("SELECT * FROM messages ORDER BY ts ASC")

    private fun query(sql: String, args: Array<String>? = null): List<Message> =
        db.readableDatabase.rawQuery(sql, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toMessage())
            }
        }

    private fun Cursor.toMessage() = Message(
        id = getString(getColumnIndexOrThrow("id")),
        peerId = getString(getColumnIndexOrThrow("peer")),
        peerName = getString(getColumnIndexOrThrow("peer_name")),
        body = getString(getColumnIndexOrThrow("body")),
        kind = MessageKind.fromWire(getString(getColumnIndexOrThrow("kind"))),
        priority = getInt(getColumnIndexOrThrow("priority")),
        outgoing = getInt(getColumnIndexOrThrow("outgoing")) == 1,
        status = MessageStatus.fromWire(getString(getColumnIndexOrThrow("status"))),
        timestamp = getLong(getColumnIndexOrThrow("ts")),
        hops = getInt(getColumnIndexOrThrow("hops")),
        path = getString(getColumnIndexOrThrow("path"))
            .split(',')
            .filter { it.isNotBlank() },
        aiSummary = getStringOrNull("ai_summary"),
        aiUrgency = getInt(getColumnIndexOrThrow("ai_urgency")).takeIf { !isNull(getColumnIndexOrThrow("ai_urgency")) },
        unread = getInt(getColumnIndexOrThrow("unread")) == 1,
        latitude = getDoubleOrNull("lat"),
        longitude = getDoubleOrNull("lon"),
        wire = getStringOrNull("wire"),
    )

    private fun Cursor.getStringOrNull(column: String): String? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }

    private fun Cursor.getDoubleOrNull(column: String): Double? =
        getColumnIndexOrThrow(column).let { if (isNull(it)) null else getDouble(it) }

    private fun Message.toValues() = ContentValues().apply {
        put("id", id)
        put("peer", peerId)
        put("peer_name", peerName)
        put("body", body)
        put("kind", kind.name)
        put("priority", priority)
        put("outgoing", if (outgoing) 1 else 0)
        put("status", status.name)
        put("ts", timestamp)
        put("hops", hops)
        put("path", path.joinToString(","))
        put("ai_summary", aiSummary)
        put("ai_urgency", aiUrgency)
        put("unread", if (unread) 1 else 0)
        putNullable("lat", latitude)
        putNullable("lon", longitude)
        put("wire", wire)
    }
}
