package no.jodal.salat.apps.kompis

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
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
    /** Ny bit av argumentene til verktøykallet, eller tom. */
    val toolArguments: String,
    val provider: String?,
    /** Forbruk og pris, i den siste hendelsen. */
    val usage: JsonObject?,
    /** Feilen OpenRouter ga midt i svaret, eller `null`. */
    val error: String?,
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
    val toolArguments = (delta?.get("tool_calls") as? JsonArray)
        ?.joinToString("") { it.jsonObjectOrNull()?.get("function")?.jsonObjectOrNull()?.string("arguments").orEmpty() }
        .orEmpty()
    return ChatChunk(content, toolArguments, event.string("provider"), event["usage"]?.jsonObjectOrNull(), error)
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
        messages: List<Pair<Role, String>>,
        tools: Boolean = true,
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
            for ((role, text) in messages) {
                addJsonObject {
                    put("role", if (role == Role.User) "user" else "assistant")
                    put("content", text)
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
        if (tools) {
            putJsonArray("tools") { addJsonObject { sayTool() } }
            putJsonObject("tool_choice") {
                put("type", "function")
                putJsonObject("function") { put("name", "say") }
            }
        }
        if (providers.isNotEmpty()) {
            putJsonObject("provider") {
                putJsonArray("order") { providers.forEach { add(it) } }
                put("allow_fallbacks", false)
            }
        }
    }.toString()
}

/**
 * Verktøyet Kompis svarer med. Feltene står i den rekkefølgen modellen skriver dem: først hva den tror
 * brukeren sa, så er det lettere å svare på det brukeren mente og ikke på det som ble hørt feil.
 */
private fun JsonObjectBuilder.sayTool() {
    put("type", "function")
    putJsonObject("function") {
        put("name", "say")
        put("description", "Si noe høyt til brukeren. Dette er den eneste måten du svarer på.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("understood") {
                    put("type", "string")
                    put(
                        "description",
                        "Hva brukeren mest sannsynlig sa, skrevet riktig. Talegjenkjenningen kan ha hørt norsk som svensk eller dansk " +
                            "eller hørt ord feil. Rett det opp. Bare det brukeren mente, uten kommentarer.",
                    )
                }
                putJsonObject("language") {
                    put("type", "string")
                    putJsonArray("enum") {
                        add("no")
                        add("en")
                        add("es")
                    }
                    put("description", "Språket du svarer på.")
                }
                putJsonObject("style") {
                    put("type", "string")
                    put("description", "Valgfritt: kort regi for stemmen, for eksempel «varm og rolig». Tomt når vanlig tone passer.")
                }
                putJsonObject("text") {
                    put("type", "string")
                    put("description", "Det som skal sies høyt, som ren tekst slik det skal høres.")
                }
            }
            putJsonArray("required") {
                add("understood")
                add("language")
                add("text")
            }
        }
    }
}
