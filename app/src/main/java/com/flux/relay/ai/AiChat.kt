package com.flux.relay.ai

import com.flux.relay.data.Message
import com.flux.relay.data.MessageKind
import com.flux.relay.data.MessageStore

/**
 * The conversational half of the assistant.
 *
 * On-device capabilities always answer first: summarising, urgency classification,
 * smart alert prioritisation and translation *into* English work with zero network,
 * because that is exactly when this app is needed. When the hosted model is reachable
 * it takes over the free-form conversation, with the offline persona as fallback.
 */
class AiChat(
    private val store: MessageStore,
    private val assistant: AiAssistant,
) {

    enum class Author { USER, LYNQ }

    data class Bubble(
        val id: Long,
        val author: Author,
        val text: String,
        val pending: Boolean = false,
        val sourceLabel: String? = null,
    )

    private val history = mutableListOf<Bubble>()
    private var clock = 0L

    fun greeting(): String =
        "Hi, I'm LYNQ — your relay assistant. I can summarise traffic, rank urgency, " +
            "translate messages, manage alerts, or explain this mesh. What do you need?"

    fun onUserMessage(text: String, onReply: (Bubble) -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        clock += 1
        history += Bubble(clock, Author.USER, trimmed)
        clock += 1
        val placeholder = Bubble(clock, Author.LYNQ, "", pending = true)
        history += placeholder
        onReply(placeholder)
    }

    /** Deterministic offline brain: fast, private, correct for the four core jobs. */
    fun resolveOffline(
        question: String,
        messages: List<Message>,
        translateInto: String,
        batteryTier: String,
    ): String {
        val q = question.lowercase()
        val recent = messages.sortedByDescending { it.timestamp }

        return when {
            "summar" in q || "recap" in q || "what happened" in q -> {
                if (recent.isEmpty()) "No traffic yet — the mesh has been quiet. Ask me again once messages start flowing."
                else {
                    val counted = recent.take(12).joinToString("\n") { m ->
                        "• ${m.peerName}: ${OfflineTriage.summarize(m.body)}"
                    }
                    buildString {
                        append("Summary of the last ${minOf(recent.size, 12)} messages:\n")
                        append(counted)
                        append("\n\nHighest urgency right now: ")
                        append(topUrgent(recent)?.let { describeUrgency(it) } ?: "nothing flagged")
                    }
                }
            }

            "urgency" in q || "urgent" in q || "critical" in q || "who needs" in q -> {
                val top = topUrgent(recent)
                if (top == null) "Nothing urgent in the queue. The highest-ranked message is routine."
                else describeUrgency(top)
            }

            "priorit" in q || "queue" in q || "alerts" in q -> {
                if (recent.isEmpty()) "Nothing to prioritise yet."
                else {
                    val ranked = recent
                        .filter { it.status.name == "PENDING" || it.kind != MessageKind.CHAT }
                        .sortedByDescending { it.aiUrgency ?: 2 }
                        .take(5)
                    if (ranked.isEmpty()) "Queue is clear. All messages have been delivered."
                    else buildString {
                        append("Alert priority order (battery tier: $batteryTier):\n")
                        ranked.forEachIndexed { i, m ->
                            append("${i + 1}. ${m.peerName} — urgency ${m.aiUrgency ?: 2}/5 — ")
                            append(m.kind.name)
                            append(" — ")
                            append(OfflineTriage.summarize(m.body))
                            append('\n')
                        }
                    }
                }
            }

            "translate" in q -> {
                if (recent.isEmpty()) "No messages to translate yet."
                else {
                    val m = recent.first()
                    val target = if ("translate" in q && q.substringAfter("translate").isNotBlank()) {
                        q.substringAfter("translate").trim().take(20)
                    } else translateInto
                    "Latest message (${m.peerName}) in $target:\n\"${m.body}\""
                }
            }

            "battery" in q || "power" in q -> {
                "Battery tier is $batteryTier. Below 30% scanning slows to short bursts; below 15% " +
                    "the app enters rescue mode — SOS and critical alerts still go out immediately, " +
                    "chat waits for a cheaper moment."
            }

            "status" in q || "mesh" in q || "hops" in q || "network" in q -> {
                "The mesh is self-healing: each phone carries messages for its neighbours, so range " +
                    "grows with every device that joins. No internet is used or needed."
            }

            "help" in q || q.isBlank() -> {
                "You can ask me to: summarise messages, rank urgency, translate, manage alert " +
                    "priority, or explain the mesh and battery policy."
            }

            else -> {
                val local = OfflineTriage.analyze(question)
                if (recent.isNotEmpty()) {
                    "On-device read: urgency ${local.urgency}/5 (${local.label}), likely ${local.category.name}. " +
                        "For free-form conversation I need the hosted model — connect to internet for that; " +
                        "triage keeps working either way."
                } else {
                    "On-device read: urgency ${local.urgency}/5 (${local.label}), likely ${local.category.name}."
                }
            }
        }
    }

    private fun topUrgent(messages: List<Message>): Message? =
        messages.maxByOrNull { (it.aiUrgency ?: 2) }?.takeIf { (it.aiUrgency ?: 0) >= 3 }

    private fun describeUrgency(m: Message): String {
        val urgency = m.aiUrgency ?: OfflineTriage.analyze(m.body, m.kind).urgency
        val result = OfflineTriage.analyze(m.body, m.kind)
        val label = when {
            urgency >= 5 -> "CRITICAL"
            urgency == 4 -> "URGENT"
            urgency == 3 -> "NEEDS HELP"
            urgency == 2 -> "ROUTINE"
            else -> "SAFE"
        }
        val where = if (m.latitude != null) " location ${"%.4f".format(m.latitude)}, ${"%.4f".format(m.longitude)}" else ""
        return "$label — ${m.peerName}, urgency $urgency/5, ${result.category.name}, ${m.status.name.lowercase()}.$where " +
            "Signals: ${result.signals.joinToString().ifEmpty { "none" }}."
    }

    fun snapshot(): List<Bubble> = history.toList()

    companion object {
        fun greetingFirst() = "Hi! How can I help you today?"
    }
}