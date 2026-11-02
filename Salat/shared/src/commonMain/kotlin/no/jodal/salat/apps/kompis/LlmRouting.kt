package no.jodal.salat.apps.kompis

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@Serializable
data class LlmRouting(
    /** manual, auto, latency eller price. */
    val strategy: String = "manual",
    /** USD per million tokens, som i OpenRouters max_price. */
    val maxInput: Double? = null,
    val maxOutput: Double? = null,
)

internal fun parsePriceLimit(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
    ?.takeIf { it.isFinite() && it >= 0 }

internal fun validPriceLimit(text: String): Boolean = text.isBlank() || parsePriceLimit(text) != null

internal fun JsonObjectBuilder.llmProviderPreferences(providers: List<String>, routing: LlmRouting) {
    if ((routing.strategy == "auto" || (providers.isEmpty() && routing.strategy == "manual")) && routing.maxInput == null && routing.maxOutput == null) return
    putJsonObject("provider") {
        when (routing.strategy) {
            "latency", "price" -> {
                put("sort", routing.strategy)
                if (providers.isNotEmpty()) putJsonArray("only") { providers.distinct().forEach { add(it) } }
            }
            "manual" -> if (providers.isNotEmpty()) {
                putJsonArray("order") { providers.distinct().forEach { add(it) } }
                put("allow_fallbacks", false)
            }
        }
        if (routing.maxInput != null || routing.maxOutput != null) putJsonObject("max_price") {
            routing.maxInput?.let { put("prompt", it) }
            routing.maxOutput?.let { put("completion", it) }
        }
    }
}
