package no.jodal.salat.apps.kompis

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import no.jodal.salat.apps.kompis.tools.KompisTool
import no.jodal.salat.apps.kompis.tools.KompisTools
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// Det språkmodellen i testen og i samtalene har felles.

/** Det som kom i én hendelse fra et svar som strømmer. */
internal data class ChatChunk(
    /** Ny tekst i svaret, eller tom. */
    val content: String,
    /** Verktøydeltaer, adskilt med indeks. */
    val toolCalls: List<ToolCallDelta>,
    val provider: String?,
    /** Forbruk og pris, i den siste hendelsen. */
    val usage: JsonObject?,
    /** Feilen OpenRouter ga midt i svaret, eller `null`. */
    val error: String?,
    val model: String? = null,
)

private val chatJson = Json { ignoreUnknownKeys = true }

/** Leser JSON-en etter `data:` i en hendelse, eller `null` hvis det ikke er JSON. */
internal fun parseChatChunk(dataJson: String): ChatChunk? {
    val event = runCatching { chatJson.parseToJsonElement(dataJson).jsonObject }.getOrNull() ?: return null
    val error = event["error"]?.takeIf { it !is JsonNull }?.let {
        it.jsonObjectOrNull()?.string("message") ?: "OpenRouter ga en feil midt i svaret."
    }
    val choice = (event["choices"] as? JsonArray)?.firstOrNull()?.jsonObjectOrNull()
    val delta = choice?.get("delta")?.jsonObjectOrNull()
    val content = delta?.string("content").orEmpty()
    val toolCalls = (delta?.get("tool_calls") as? JsonArray)?.mapNotNull { element ->
        val call = element.jsonObjectOrNull() ?: return@mapNotNull null
        val index = call.int("index") ?: return@mapNotNull null
        val function = call["function"]?.jsonObjectOrNull()
        ToolCallDelta(index, call.string("id"), function?.string("name"), function?.string("arguments").orEmpty())
    }.orEmpty()
    return ChatChunk(content, toolCalls, event.string("provider"), event["usage"]?.jsonObjectOrNull(), error, event.string("model"))
}

internal fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.int(key: String): Int? = (get(key) as? JsonPrimitive)?.intOrNull

/** Forespørselen til `chat/completions`. */
internal object LlmRequest {
    /**
     * Samtalen [messages] med [prompt] først. [model] er modellen fra listen hos OpenRouter, eller
     * `null` hvis listen ikke er hentet. [providers] tom lar OpenRouter velge leverandør.
     */
    fun body(
        modelId: String,
        providers: List<String>,
        model: OpenRouterModel?,
        prompt: String,
        messages: List<Message>,
        tools: List<KompisTool> = KompisTools().all,
        routing: LlmRouting = LlmRouting(),
    ): String = buildJsonObject {
        put("model", modelId)
        put("stream", true)
        put("temperature", 0.7)
        put("max_tokens", 600)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", prompt)
            }
            for (message in messages) {
                addJsonObject {
                    put("role", when (message.role) {
                        Role.User -> "user"
                        Role.Kompis -> "assistant"
                        Role.Tool -> "tool"
                    })
                    put("content", message.text)
                    message.toolCallId?.let { put("tool_call_id", it) }
                    if (message.toolCalls.isNotEmpty()) putJsonArray("tool_calls") {
                        message.toolCalls.forEach { call -> addJsonObject {
                            put("id", call.id)
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", call.name)
                                put("arguments", call.arguments)
                            }
                        } }
                    }
                }
            }
        }
        // Tenking før svaret gjør at det tar mye lengre tid før noe kan leses opp.
        // Før modellisten er hentet, er det trygt å be om ingen tenking.
        val reasoning = model?.reasoning
        when {
            model == null -> putJsonObject("reasoning") { put("effort", "none") }
            reasoning == null || reasoning.mandatory -> Unit
            reasoning.supportsNone -> putJsonObject("reasoning") { put("effort", "none") }
            else -> putJsonObject("reasoning") { put("enabled", false) }
        }
        if (tools.isNotEmpty()) {
            putJsonArray("tools") {
                tools.forEach { tool -> addJsonObject {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.parameters)
                    }
                } }
            }
            put("tool_choice", "required")
        }
        llmProviderPreferences(providers, routing)
    }.toString()
}
