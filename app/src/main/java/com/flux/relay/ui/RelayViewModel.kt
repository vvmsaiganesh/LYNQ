package com.flux.relay.ui

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flux.relay.RelayApp
import com.flux.relay.ai.AiAssistant
import com.flux.relay.ai.AiChat
import com.flux.relay.ai.BatteryTiers
import com.flux.relay.ai.OfflineTriage
import com.flux.relay.crypto.Identity
import com.flux.relay.data.KnownPeer
import com.flux.relay.data.Message
import com.flux.relay.data.MessageKind
import com.flux.relay.mesh.MeshEngine
import com.flux.relay.mesh.MeshEvent
import com.flux.relay.mesh.MeshStats
import com.flux.relay.mesh.OnlinePeer
import com.flux.relay.mesh.BatteryPolicy
import com.flux.relay.mesh.RelayService
import com.flux.relay.mesh.Topology
import com.flux.relay.transport.RadioState
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Radar : Screen
    data object Chats : Screen
    data class Thread(val peerId: String) : Screen
    data object Sos : Screen
    data object Assistant : Screen
    data object Settings : Screen
}

data class ThreadSummary(
    val peerId: String,
    val title: String,
    val last: Message,
    val unread: Int,
    val isBroadcast: Boolean,
)

/**
 * Single state holder for the whole app. Compose observes plain mutable state here;
 * there is deliberately no MVI ceremony because every screen is driven by the same
 * five facts (identity, peers, topology, messages, radio).
 */
class RelayViewModel(application: Application) : AndroidViewModel(application) {

    private val services = (application as RelayApp).services

    var identity: Identity? by mutableStateOf(services.identityManager.load())
        private set

    var screen: Screen by mutableStateOf<Screen>(Screen.Radar)
        private set

    private val backStack = mutableListOf<Screen>()

    var messages: List<Message> by mutableStateOf(emptyList())
        private set

    var knownPeers: List<KnownPeer> by mutableStateOf(emptyList())
        private set

    var online: List<OnlinePeer> by mutableStateOf(emptyList())
        private set

    var topology: Topology by mutableStateOf(Topology())
        private set

    var radio: RadioState by mutableStateOf(RadioState.OFF)
        private set

    var stats: MeshStats by mutableStateOf(MeshStats())
        private set

    var lowPower: Boolean by mutableStateOf(false)
        private set

    var batteryTier: BatteryTiers.Tier by mutableStateOf(BatteryTiers.Tier.NORMAL)
        private set

    var banner: String? by mutableStateOf(null)
        private set

    var meshReady: Boolean by mutableStateOf(false)
        private set

    private var mesh: MeshEngine? = null
    private var meshRequested = false

    private val ai = AiAssistant(application, services.messageStore)

    var aiOnline: Boolean by mutableStateOf(false)
        private set

    var aiConfigured: Boolean by mutableStateOf(ai.configured)
        private set

    var aiBusy: Set<String> by mutableStateOf(emptySet())
        private set

    var aiOutcomes: Map<String, AiAssistant.Outcome> by mutableStateOf(emptyMap())
        private set

    var aiLanguage: String by mutableStateOf("English")
        private set

    var threads: List<ThreadSummary> by mutableStateOf(emptyList())
        private set

    val activeThreadPeer: KnownPeer?
        get() = (screen as? Screen.Thread)?.let { s ->
            knownPeers.firstOrNull { it.identity.fingerprint == s.peerId }
        }

    /** Messages for the thread currently open, oldest first. */
    val activeMessages: List<Message> by derivedStateOf {
        val id = (screen as? Screen.Thread)?.peerId ?: return@derivedStateOf emptyList()
        messages.filter { it.peerId == id }
    }

    init {
        services.warmUp()
        if (BatteryPolicy.shouldConserve(getApplication())) {
            // Under 15%: prefer staying alive over fast discovery.
            lowPower = true
        }
        batteryTier = BatteryTiers.tierFor(BatteryPolicy.level(getApplication()))
        viewModelScope.launch {
            services.messageStore.messages.collect {
                messages = it
                recomputeThreads()
            }
        }
        viewModelScope.launch {
            services.peerStore.peers.collect { knownPeers = it }
        }
        viewModelScope.launch { ai.online.collect { aiOnline = it } }
    }

    // ------------------------------------------------------------- identity & mesh

    fun createIdentity(name: String) {
        val created = services.identityManager.create(name)
        identity = created
        startMesh()
    }

    fun renameIdentity(name: String) {
        services.identityManager.rename(name)
        identity = services.identityManager.load()
    }

    fun startMesh() {
        if (meshRequested) return
        val me = identity ?: return
        meshRequested = true
        val engine = services.mesh(me)
        if (lowPower) engine.setLowPower(true)
        mesh = engine
        meshReady = true
        // Foreground service keeps the radio alive and the outbox draining with the screen off.
        runCatching { RelayService.start(services.appContext) }
        viewModelScope.launch { engine.online.collect { online = it } }
        viewModelScope.launch { engine.topology.collect { topology = it } }
        viewModelScope.launch { engine.radio.collect { radio = it } }
        viewModelScope.launch { engine.stats.collect { stats = it } }
        viewModelScope.launch { engine.events.collect(::onEvent) }
    }

    private fun onEvent(event: MeshEvent) {
        when (event) {
            is MeshEvent.PeerJoined -> banner = "${event.name.ifBlank { "A device" }} joined the mesh"
            is MeshEvent.PeerLeft -> banner = "A device left the mesh"
            is MeshEvent.Received -> {
                banner = "New ${if (event.message.isBroadcast) "broadcast" else "message"}"
                // Emergencies get triaged immediately so the dashboard is useful on arrival.
                // Offline triage is local and free; the online refinement is best-effort.
                if (event.message.kind != MessageKind.CHAT) analyze(event.message)
            }
            is MeshEvent.Delivered -> banner = "Delivered · ${event.hops} hop${if (event.hops == 1) "" else "s"}"
            is MeshEvent.Relayed -> banner = "Relayed via ${event.via}"
            is MeshEvent.Forwarded -> Unit
            is MeshEvent.Dropped -> Unit
        }
    }

    fun clearBanner() {
        banner = null
    }

    // ------------------------------------------------------------- actions

    fun sendMessage(text: String, to: KnownPeer?) {
        val engine = mesh ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            engine.sendText(
                to = to?.identity,
                text = text,
                kind = if (to == null) MessageKind.ALERT else MessageKind.CHAT,
            )
        }
    }

    fun sendSos(text: String, location: Pair<Double, Double>?) {
        val engine = mesh ?: return
        viewModelScope.launch {
            engine.sendText(
                to = null,
                text = text.ifBlank { "I need help. Please respond if you can hear this." },
                kind = MessageKind.SOS,
                location = location,
            )
        }
    }

    fun openThread(peerId: String) {
        backStack += screen
        screen = Screen.Thread(peerId)
        viewModelScope.launch { services.messageStore.markConversationRead(peerId) }
    }

    fun navigate(target: Screen) {
        backStack += screen
        screen = target
    }

    fun back(): Boolean {
        val previous = backStack.removeLastOrNull() ?: return false
        screen = previous
        return true
    }

    fun returnToRadar() {
        backStack.clear()
        screen = Screen.Radar
    }

    // ------------------------------------------------------------- floating assistant

    var assistantOpen: Boolean by mutableStateOf(false)
        private set

    fun openAssistant() {
        assistantOpen = true
    }

    fun closeAssistant() {
        assistantOpen = false
    }

    fun setLowPowerMode(enabled: Boolean) {
        lowPower = enabled
        mesh?.setLowPower(enabled)
    }

    // ------------------------------------------------------------- AI chat (glass assistant)

    var aiChat: List<AiChat.Bubble> by mutableStateOf(emptyList())
        private set

    var aiChatBusy: Boolean by mutableStateOf(false)
        private set

    /** True when the hosted model answered the last turn; drives the online/offline chip in the sheet. */
    var aiChatOnlineAnswer: Boolean by mutableStateOf(false)
        private set

    private val chat = AiChat(services.messageStore, ai)

    fun aiGreeting(): String = chat.greeting()

    fun sendToAssistant(text: String) {
        if (text.isBlank() || aiChatBusy) return
        var placeholder: AiChat.Bubble? = null
        chat.onUserMessage(text) { bubble ->
            if (bubble.pending) {
                placeholder = bubble
                aiChat = chat.snapshot()
            }
        }
        aiChat = chat.snapshot()
        aiChatBusy = true
        viewModelScope.launch {
            val tier = batteryTier
            val reply: String = if (ai.configured && ai.online.value && BatteryTiers.allowsOnlineAi(tier)) {
                ai.chat(question = text, context = messages, batteryTier = tier.label)
                    ?: fallback(text)
            } else {
                fallback(text)
            }
            aiChatOnlineAnswer = ai.configured && ai.online.value && BatteryTiers.allowsOnlineAi(tier)
            aiChat = chat.snapshot().map { if (it.pending) it.copy(text = reply, pending = false, sourceLabel = if (aiChatOnlineAnswer) "Featherless" else "On-device") else it }
            aiChatBusy = false
        }
    }

    private fun fallback(text: String): String =
        chat.resolveOffline(text, messages, aiLanguage, BatteryTiers.describe(batteryTier))

    fun clearAiChat() {
        aiChat = emptyList()
    }

    fun verifyPeer(fingerprint: String, verified: Boolean) {
        viewModelScope.launch { services.peerStore.setVerified(fingerprint, verified) }
    }

    fun blockPeer(fingerprint: String, blocked: Boolean) {
        viewModelScope.launch { services.peerStore.setBlocked(fingerprint, blocked) }
    }

    fun clearBannerSoon() {
        banner = null
    }

    // ------------------------------------------------------------- AI assistant (optional)

    /** Runs offline triage always, then refines through Featherless when possible. */
    fun analyze(message: Message, targetLanguage: String? = aiLanguage) {
        if (message.id in aiBusy) return
        aiBusy = aiBusy + message.id
        viewModelScope.launch {
            val outcome = runCatching { ai.triage(message, targetLanguage) }.getOrNull()
            if (outcome != null) aiOutcomes = aiOutcomes + (message.id to outcome)
            aiBusy = aiBusy - message.id
        }
    }

    fun chooseAiLanguage(language: String) {
        aiLanguage = language
    }

    fun aiOutcomeFor(messageId: String): AiAssistant.Outcome? = aiOutcomes[messageId]

    /** Messages ranked for the triage dashboard: most urgent first, newest within a band. */
    fun triageQueue(): List<Message> = messages
        .filter { !it.outgoing }
        .sortedWith(
            compareByDescending<Message> {
                aiOutcomes[it.id]?.urgency
                    ?: it.aiUrgency
                    ?: OfflineTriage.analyze(it.body, it.kind).urgency
            }.thenByDescending { it.timestamp },
        )

    override fun onCleared() {
        ai.close()
        super.onCleared()
    }

    private fun recomputeThreads() {
        threads = messages
            .groupBy { it.peerId }
            .map { (peerId, msgs) ->
                val last = msgs.maxByOrNull { it.timestamp } ?: return@map null
                val known = knownPeers.firstOrNull { it.identity.fingerprint == peerId }
                ThreadSummary(
                    peerId = peerId,
                    title = when {
                        peerId == Message.BROADCAST -> "Nearby broadcast"
                        else -> last.peerName.ifBlank {
                            known?.identity?.displayName ?: peerId.take(6)
                        }
                    },
                    last = last,
                    unread = msgs.count { it.unread },
                    isBroadcast = peerId == Message.BROADCAST,
                )
            }
            .filterNotNull()
            .sortedByDescending { it.last.timestamp }
    }

    /** Peers to show in the compose picker: everyone we have ever seen, newest first. */
    fun reachablePeers(): List<KnownPeer> = knownPeers.filter { !it.blocked }
}
