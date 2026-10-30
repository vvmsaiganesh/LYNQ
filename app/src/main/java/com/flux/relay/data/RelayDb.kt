package com.flux.relay.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Plain SQLite. Deliberately no Room/KSP: annotation processing is the single most
 * common source of 30-minute "why won't it build" detours, and the schema below is
 * small enough that hand-written statements are cheaper than the build risk.
 */
internal class RelayDb(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE messages (
                id TEXT PRIMARY KEY NOT NULL,
                peer TEXT NOT NULL,
                peer_name TEXT NOT NULL,
                body TEXT NOT NULL,
                kind TEXT NOT NULL,
                priority INTEGER NOT NULL,
                outgoing INTEGER NOT NULL,
                status TEXT NOT NULL,
                ts INTEGER NOT NULL,
                hops INTEGER NOT NULL DEFAULT 0,
                path TEXT NOT NULL DEFAULT '',
                ai_summary TEXT,
                ai_urgency INTEGER,
                unread INTEGER NOT NULL DEFAULT 0,
                lat REAL,
                lon REAL,
                wire TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_messages_ts ON messages(ts)")
        db.execSQL("CREATE INDEX idx_messages_peer ON messages(peer, ts)")

        db.execSQL(
            """
            CREATE TABLE peers (
                fingerprint TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                sign_pub TEXT NOT NULL,
                agree_pub TEXT NOT NULL,
                last_seen INTEGER NOT NULL,
                hops INTEGER NOT NULL DEFAULT 1,
                verified INTEGER NOT NULL DEFAULT 0,
                blocked INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Hackathon schema: destructive upgrade keeps the demo recoverable across builds.
        db.execSQL("DROP TABLE IF EXISTS messages")
        db.execSQL("DROP TABLE IF EXISTS peers")
        onCreate(db)
    }

    companion object {
        private const val NAME = "relay.db"
        private const val VERSION = 1
    }
}

internal fun ContentValues.putNullable(key: String, value: Double?) {
    if (value == null) putNull(key) else put(key, value)
}
