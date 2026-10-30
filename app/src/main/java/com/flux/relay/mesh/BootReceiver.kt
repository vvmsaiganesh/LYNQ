package com.flux.relay.mesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flux.relay.RelayApp

/**
 * Recovery after a reboot: if this device already has an identity, bring the mesh back up
 * without the user opening the app. Undelivered messages are replayed from SQLite by
 * MeshEngine.restoreOutbox(), so a phone that was off during the alert still catches up.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? RelayApp ?: return
        if (app.services.identityManager.load() == null) return
        runCatching { RelayService.start(context) }
    }
}
