package com.flux.relay.ai

import com.flux.relay.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Thin client for Featherless.ai's OpenAI-compatible endpoint.
 *
 * This is the *optional* half of the assistant. It is never on the message path: the
 * mesh never waits for it, and every call fails soft back to [OfflineTriage].
 */
class FeatherlessClient(
    private val apiKey: String = BuildConfig.FEATHERLESS_API_KEY,
    private val baseUrl: String = BuildConfig.FEATHERLESS_BASE_URL,
    private val model: String = MODEL,
) {

    data class Refinement(
        val summary: String?,
        val urgency: Int?,
        val category: String?,
        val translation: String?,
        val language: String?,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    val configured: Boolean get() = apiKey.isNotBlank()

    /** Returns null when offline, unconfigured, rate-limited or the model misbehaves. */
    suspend fun refine(
        body: String,
        kind: String,
        targetLanguage: String? = null,
    ): Refinement? = withContext(Dispatchers.IO) {
        if (!configured || body.isBlank()) return@withContext null

        val instruction = buildString {
            append("Translate the message into ")
            append(if (targetLanguage.isNullOrBlank()) "English" else targetLanguage)
            append(" in the \"translation\" field; if it is already in that language, return an empty string.")
        }

        val payload = buildJsonObject {
            put("model", model)
            put("temperature", 0.1)
            put("max_tokens", 320)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put(
                                "content",
                                """
                                You are an emergency triage assistant for an offline mesh messenger.
                                Reply with ONLY minified JSON, no markdown, no prose, with exactly these keys:
                                {"summary":string (max 15 words),"urgency":integer 1-5 (5 = life-threatening),
                                "category":"MEDICAL|TRAPPED|FIRE|VIOLENCE|WATER_FOOD|SHELTER|SAFE|GENERAL",
                                "translation":string,"language":string}
                                $instruction
                                Do not invent facts that are not in the message. Urgency assists a human; it never replaces their judgment.
                                """.trimIndent(),
                            )
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", "Message type: $kind\nMessage: $body")
                        },
                    )
                },
            )
        }

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val text = response.body?.string() ?: return@withContext null
                parseCompletion(text)
            }
        }.getOrNull()
    }

    private fun parseCompletion(raw: String): Refinement? {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val content = root["choices"]
            ?.let { it as? kotlinx.serialization.json.JsonArray }
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return null
        return parseStructured(stripFences(content))
    }

    private fun parseStructured(content: String): Refinement? {
        val obj: JsonObject = runCatching {
            json.parseToJsonElement(content.substring(content.indexOf('{'), content.lastIndexOf('}') + 1)).jsonObject
        }.getOrNull() ?: return null

        fun str(key: String): String? = obj[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

        val urgency = obj["urgency"]?.jsonPrimitive?.intOrNull?.coerceIn(1, 5)
        val category = str("category")?.uppercase()?.takeIf {
            it in setOf("MEDICAL", "TRAPPED", "FIRE", "VIOLENCE", "WATER_FOOD", "SHELTER", "SAFE", "GENERAL")
        }
        return Refinement(
            summary = str("summary")?.take(180),
            urgency = urgency,
            category = category,
            translation = str("translation"),
            language = str("language"),
        )
    }

    private fun stripFences(text: String): String =
        text.replace("```json", "").replace("```", "").trim()

    private companion object {
        // Small, fast, widely hosted open model. Swap freely; the wire contract is OpenAI-compatible.
        const val MODEL = "Qwen/Qwen2.5-7B-Instruct"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
