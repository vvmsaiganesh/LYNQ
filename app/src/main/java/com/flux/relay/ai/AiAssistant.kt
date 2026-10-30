package com.flux.relay.ai

import android.content.Context
import com.flux.relay.data.Message
import com.flux.relay.data.MessageStore
import kotlinx.coroutines.flow.StateFlow

/**
 * Facade used by the UI. Offline triage always runs; the hosted model is layered on top
 * only when both a key and validated internet exist. Results are written back into the
 * message row so triage survives a restart.
 */
class AiAssistant(context: Context, private val store: MessageStore) {

    enum class Source { OFFLINE, FEATHERLESS }

    data class Outcome(
        val source: Source,
        val summary: String,
        val urgency: Int,
        val category: String,
        val translation: String?,
        val language: String,
        val signals: List<String> = emptyList(),
        val onlineNote: String? = null,
    )

    private val monitor = NetworkMonitor(context.applicationContext)
    private val client = FeatherlessClient()

    val online: StateFlow<Boolean> = monitor.online
    val configured: Boolean get() = client.configured

    suspend fun triage(message: Message, targetLanguage: String? = null): Outcome {
        val local = OfflineTriage.analyze(message.body, message.kind)
        var summary = local.summary
        var urgency = local.urgency
        var category = local.category.name
        var translation: String? = null
        var language = local.language
        var source = Source.OFFLINE
        var note: String? = null

        if (configured && online.value) {
            val refined = client.refine(
                body = message.body,
                kind = message.kind.name,
                targetLanguage = targetLanguage?.let(OfflineTriage::languageName),
            )
            if (refined == null) {
                note = "Featherless unreachable — showing on-device triage."
            } else {
                source = Source.FEATHERLESS
                summary = refined.summary ?: summary
                urgency = refined.urgency ?: urgency
                category = refined.category ?: category
                translation = refined.translation
                language = refined.language ?: language
            }
        } else if (!configured) {
            note = "No Featherless key configured — on-device triage only."
        }

        store.setAiFields(message.id, summary, urgency)
        return Outcome(
            source = source,
            summary = summary,
            urgency = urgency,
            category = category,
            translation = translation?.takeIf { it.isNotBlank() },
            language = language,
            signals = local.signals,
            onlineNote = note,
        )
    }

    fun close() = monitor.close()
}
