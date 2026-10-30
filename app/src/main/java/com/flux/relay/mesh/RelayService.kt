package com.flux.relay.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.flux.relay.R
import com.flux.relay.RelayApp
import com.flux.relay.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the mesh alive with the screen off.
 *
 * Foreground, `connectedDevice` type: Android is explicit that radios in the background
 * are otherwise suspended, which would silently break store-and-forward. The notification
 * is also honest signalling to the user that their phone is still relaying for others.
 */
class RelayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var lastPeerCount = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting mesh…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val services = (application as RelayApp).services
        val identity = services.identityManager.load()
        if (identity == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val engine = services.mesh(identity)
        if (watcher == null) {
            watcher = scope.launch {
                engine.online.collect { peers ->
                    if (peers.size != lastPeerCount) {
                        lastPeerCount = peers.size
                        notify(
                            when (peers.size) {
                                0 -> "Listening for nearby nodes"
                                1 -> "1 node linked · relaying"
                                else -> "${peers.size} nodes linked · relaying"
                            },
                        )
                    }
                }
            }
        }
        notify(if (lastPeerCount <= 0) "Listening for nearby nodes" else "$lastPeerCount nodes linked · relaying")
        return START_STICKY
    }

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_relay)
            .setContentTitle("Relay mesh active")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Mesh status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows that this phone is still relaying messages offline."
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "relay.mesh"
        private const val NOTIFICATION_ID = 4101

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RelayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RelayService::class.java))
        }
    }
}
