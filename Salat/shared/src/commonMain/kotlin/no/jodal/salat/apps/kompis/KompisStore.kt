package no.jodal.salat.apps.kompis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import no.jodal.salat.NativeBridge

/** Holder på samtalene og innstillingene i Kompis, og lagrer dem på telefonen. */
class KompisStore private constructor(private val bridge: NativeBridge) {
    var data by mutableStateOf(load())
        private set

    var apiKey by mutableStateOf(bridge.loadSecret(ApiKeyName).orEmpty())
        private set

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    /** Nøkkelen til OpenRouter, for å teste tale til tekst. */
    var openRouterKey by mutableStateOf(bridge.loadSecret(OpenRouterKeyName).orEmpty())
        private set

    val hasOpenRouterKey: Boolean get() = openRouterKey.isNotBlank()

    fun update(transform: (KompisData) -> KompisData) {
        data = transform(data)
        bridge.saveText(StorageKey, json.encodeToString(KompisData.serializer(), data))
    }

    fun saveApiKey(key: String) {
        apiKey = key.trim()
        bridge.saveSecret(ApiKeyName, apiKey)
    }

    fun saveOpenRouterKey(key: String) {
        openRouterKey = key.trim()
        bridge.saveSecret(OpenRouterKeyName, openRouterKey)
    }

    fun conversation(id: String): Conversation? = data.conversations.firstOrNull { it.id == id }

    fun deleteConversation(id: String) = update { it.copy(conversations = it.conversations.filter { c -> c.id != id }) }

    fun renameConversation(id: String, title: String) = update {
        it.copy(conversations = it.conversations.map { c -> if (c.id == id) c.copy(title = title.trim()) else c })
    }

    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private fun load(): KompisData = bridge.loadText(StorageKey)
        ?.let { runCatching { json.decodeFromString(KompisData.serializer(), it) }.getOrNull() }
        ?: KompisData()

    companion object {
        private const val StorageKey = "kompis.data"
        private const val ApiKeyName = "kompis.geminiApiKey"
        private const val OpenRouterKeyName = "kompis.openRouterApiKey"
        private val json = Json { ignoreUnknownKeys = true }
        private var instance: KompisStore? = null

        fun get(bridge: NativeBridge): KompisStore = instance ?: KompisStore(bridge).also { instance = it }
    }
}
