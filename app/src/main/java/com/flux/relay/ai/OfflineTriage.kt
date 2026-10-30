package com.flux.relay.ai

import com.flux.relay.data.MessageKind

/**
 * On-device emergency triage.
 *
 * The AI assistant is optional and needs internet, but *prioritisation must not* — the
 * whole premise of this app is that the network is gone. So urgency is always computed
 * here first, from a transparent lexicon, and a Featherless response can only refine it.
 *
 * Scores are deliberately conservative: over-ranking an emergency is cheap, under-ranking
 * one is not.
 */
object OfflineTriage {

    enum class Category { MEDICAL, TRAPPED, FIRE, VIOLENCE, WATER_FOOD, SHELTER, SAFE, GENERAL }

    data class Result(
        val urgency: Int,
        val category: Category,
        val summary: String,
        val signals: List<String>,
        val language: String,
    ) {
        val label: String
            get() = when (urgency) {
                5 -> "CRITICAL"
                4 -> "URGENT"
                3 -> "NEEDS HELP"
                2 -> "ROUTINE"
                else -> "SAFE"
            }
    }

    private val critical = listOf(
        "not breathing", "can't breathe", "cannot breathe", "unconscious", "unresponsive",
        "severe bleeding", "bleeding badly", "heart attack", "stroke", "drowning",
        "trapped", "buried", "collapsed", "crushed", "chest pain", "seizure",
        "no pulse", "overdose", "poison", "electrocuted",
    )

    private val urgent = listOf(
        "help", "injured", "injury", "wounded", "bleeding", "broken", "fracture",
        "stuck", "smoke", "fire", "burning", "flood", "rising water", "missing",
        "child", "baby", "elderly", "pregnant", "diabetic", "insulin", "oxygen",
        "medicine", "medication", "asthma", "allergic", "attack", "gun", "weapon",
        "danger", "threat", "sos", "emergency", "rescue", "ambulance",
    )

    private val needs = listOf(
        "water", "food", "shelter", "blanket", "charging", "charge", "battery",
        "lost", "cold", "hot", "hungry", "thirsty", "evacuate", "evacuation",
        "address", "location", "meet", "find you",
    )

    private val safe = listOf(
        "i am safe", "we are safe", "all safe", "made it", "reached safely",
        "no injuries", "everyone is okay", "everyone is ok", "we are okay", "we are ok",
    )

    /** Category wins are ordered by consequence, not by keyword count. */
    private fun categoryOf(text: String): Category = when {
        critical.any { it in text } || listOf("bleeding", "injury", "injured", "wounded", "breathing")
            .any { it in text } -> Category.MEDICAL
        listOf("trapped", "buried", "collapsed", "stuck", "rubble").any { it in text } -> Category.TRAPPED
        listOf("fire", "smoke", "burning", "gas leak").any { it in text } -> Category.FIRE
        listOf("gun", "weapon", "attack", "threat", "violence", "shooting").any { it in text } -> Category.VIOLENCE
        listOf("water", "food", "hungry", "thirsty", "medicine", "insulin").any { it in text } -> Category.WATER_FOOD
        listOf("shelter", "blanket", "cold", "evacuate", "roof").any { it in text } -> Category.SHELTER
        safe.any { it in text } -> Category.SAFE
        else -> Category.GENERAL
    }

    fun analyze(body: String, kind: MessageKind = MessageKind.CHAT): Result {
        val text = body.lowercase().replace(Regex("\\s+"), " ").trim()

        val hits = buildList {
            critical.filter { it in text }.forEach { add(it) }
            urgent.filter { it in text }.forEach { add(it) }
            needs.filter { it in text }.forEach { add(it) }
        }.distinct()

        var urgency = when {
            critical.any { it in text } -> 5
            urgent.count { it in text } >= 2 -> 4
            urgent.any { it in text } -> 3
            needs.any { it in text } -> 2
            safe.any { it in text } -> 1
            else -> 2
        }

        // An explicit SOS or alert is never allowed to look routine, even with bland wording.
        if (kind == MessageKind.SOS) urgency = maxOf(urgency, 4)
        if (kind == MessageKind.ALERT) urgency = maxOf(urgency, 3)
        if (text.isBlank()) urgency = if (kind == MessageKind.SOS) 4 else 2

        return Result(
            urgency = urgency.coerceIn(1, 5),
            category = categoryOf(text),
            summary = summarize(body),
            signals = hits.take(4),
            language = detectLanguage(body),
        )
    }

    /** First sentence-ish, hard-capped: shelter screens are small and hands are shaking. */
    fun summarize(body: String): String {
        val clean = body.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return "(empty message)"
        val sentence = clean.split(Regex("(?<=[.!?])\\s+")).firstOrNull() ?: clean
        val head = if (sentence.length > MAX_SUMMARY) {
            sentence.take(MAX_SUMMARY).trimEnd().substringBeforeLast(' ') + "…"
        } else {
            sentence
        }
        return if (head.length < clean.length && !head.endsWith("…")) "$head…" else head
    }

    /** Coarse script detection — enough to pick a sensible default translation target. */
    fun detectLanguage(text: String): String {
        val counts = mapOf(
            "hi" to text.count { it in '\u0900'..'\u097F' },
            "te" to text.count { it in '\u0C00'..'\u0C7F' },
            "ar" to text.count { it in '\u0600'..'\u06FF' },
            "zh" to text.count { it in '\u4E00'..'\u9FFF' },
            "ru" to text.count { it in '\u0400'..'\u04FF' },
        )
        val best = counts.maxByOrNull { it.value }
        return if (best != null && best.value > 0) best.key else "en"
    }

    fun languageName(code: String): String = when (code) {
        "hi" -> "Hindi"
        "te" -> "Telugu"
        "ta" -> "Tamil"
        "es" -> "Spanish"
        "fr" -> "French"
        "ar" -> "Arabic"
        "zh" -> "Chinese"
        "ru" -> "Russian"
        else -> "English"
    }

    private const val MAX_SUMMARY = 96
}
