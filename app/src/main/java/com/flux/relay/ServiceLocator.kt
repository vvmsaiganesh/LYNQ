package com.flux.relay

import android.content.Context
import com.flux.relay.crypto.Identity
import com.flux.relay.crypto.IdentityManager
import com.flux.relay.data.MessageStore
import com.flux.relay.data.PeerStore
import com.flux.relay.mesh.MeshEngine
import com.flux.relay.transport.NearbyTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hand-rolled dependency graph. No Hilt: annotation processing is build-risk we cannot
 * afford in a 24h window, and this app has exactly one object graph.
 */
class ServiceLocator(context: Context) {

    val appContext: Context = context.applicationContext

    /** Outlives Activities: the mesh must keep running while the UI is recreated. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val identityManager = IdentityManager(appContext)
    val messageStore = MessageStore(appContext)
    val peerStore = PeerStore(appContext)

    @Volatile
    private var engine: MeshEngine? = null

    /** The mesh starts exactly once, the first time an identity exists. */
    fun mesh(identity: Identity): MeshEngine = synchronized(this) {
        engine ?: MeshEngine(
            identity = identity,
            transport = NearbyTransport(
                context = appContext,
                serviceId = SERVICE_ID,
                nickname = "${identity.displayName} ${identity.fingerprint.take(4)}",
                scope = scope,
            ),
            messages = messageStore,
            peers = peerStore,
            scope = scope,
        ).also {
            it.start()
            engine = it
        }
    }

    fun warmUp() {
        scope.launch {
            messageStore.refresh()
            peerStore.refresh()
        }
    }

    fun stopMesh() {
        engine?.stop()
        engine = null
    }

    private companion object {
        const val SERVICE_ID = "com.flux.relay.mesh.v1"
    }
}
