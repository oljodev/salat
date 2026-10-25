package no.jodal.salat.apps.kompis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import no.jodal.salat.ChatListener
import no.jodal.salat.NativeBridge

/** En melding i testchatten. Svarene fra Kompis har målinger, med mindre de ble stoppet. */
data class LlmMessage(val role: Role, val text: String, val stats: LlmStats? = null)

/** Hvor raskt et svar kom. Tidene er fra forespørselen ble sendt. */
data class LlmStats(
    val modelName: String,
    val provider: String?,
    val firstTokenMillis: Long?,
    /** Når den første hele setningen var klar, altså når den kunne blitt lest opp. */
    val firstSentenceMillis: Long?,
    val totalMillis: Long,
    val promptTokens: Int?,
    val cachedTokens: Int?,
    val completionTokens: Int?,
    val reasoningTokens: Int?,
    val costUsd: Double?,
) {
    /** Hvor mange tokens i sekundet som kom etter det første. */
    val tokensPerSecond: Int?
        get() {
            val tokens = completionTokens ?: return null
            val seconds = (totalMillis - (firstTokenMillis ?: return null)) / 1000.0
            return if (seconds > 0.05 && tokens > 1) (tokens / seconds).toInt() else null
        }
}

/** Testen av språkmodellen: en chat med strømming, som viser hvor raskt svarene kommer. */
class LlmTest(
    private val store: KompisStore,
    private val bridge: NativeBridge,
    private val now: () -> LocalDateTime = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) },
) : ChatListener {
    /** Samtalen så langt, uten svaret som strømmer. */
    var messages by mutableStateOf(emptyList<LlmMessage>())
        private set

    /** Svaret som strømmer, eller `null`. */
    var draft by mutableStateOf<String?>(null)
        private set

    val isStreaming: Boolean get() = draft != null

    var message by mutableStateOf<String?>(null)
        private set

    var models by mutableStateOf(ModelCache.llm)
        private set

    var isLoadingModels by mutableStateOf(false)
        private set

    var modelsError by mutableStateOf<String?>(null)
        private set

    /** Leverandørene som kjører modellen. */
    var providers by mutableStateOf(emptyList<OpenRouterProvider>())
        private set

    var isLoadingProviders by mutableStateOf(false)
        private set

    val modelId: String get() = store.data.settings.llmModel

    /** Leverandørene som skal prøves, i rekkefølge. Tom lar OpenRouter velge. */
    val providerOrder: List<String> get() = store.data.settings.llmProviders

    fun modelName(id: String): String = models.firstOrNull { it.id == id }?.shortName ?: id.substringAfter('/')

    // Det som måles mens et svar strømmer.
    private var answer = StringBuilder()
    private var sentModelName = ""
    private var provider: String? = null
    private var firstTokenMillis: Long? = null
    private var firstSentenceMillis: Long? = null
    private var usage: JsonObject? = null

    fun open() = bridge.openRouterWarmUp()

    fun close() {
        if (isStreaming) stop()
    }

    fun loadModels() {
        if (!store.hasOpenRouterKey || isLoadingModels) return
        isLoadingModels = true
        modelsError = null
        bridge.openRouterGet(OpenRouter.LlmModelsPath, store.openRouterKey) { body, error ->
            isLoadingModels = false
            val parsed = body?.let { runCatching { OpenRouter.parseModels(it) }.getOrNull() }
            if (parsed == null) {
                modelsError = error ?: "Forsto ikke modellisten fra OpenRouter."
            } else {
                models = parsed
                ModelCache.llm = parsed
            }
        }
    }

    fun loadProviders() {
        if (!store.hasOpenRouterKey) return
        val id = modelId
        isLoadingProviders = true
        bridge.openRouterGet(OpenRouter.endpointsPath(id), store.openRouterKey) { body, _ ->
            // Modellen kan ha blitt byttet i mellomtiden.
            if (id != modelId) return@openRouterGet
            isLoadingProviders = false
            providers = body?.let { runCatching { OpenRouter.parseProviders(it) }.getOrNull() }.orEmpty()
        }
    }

    /** Bytter modell. Leverandørene velges på nytt, bortsett fra for standardmodellen. */
    fun selectModel(id: String) {
        if (id == modelId) return
        val order = if (id == DefaultLlmModel) DefaultLlmProviders else emptyList()
        store.update { it.copy(settings = it.settings.copy(llmModel = id, llmProviders = order)) }
        providers = emptyList()
        loadProviders()
    }

    /** Legger leverandøren sist i rekkefølgen, eller fjerner den. */
    fun toggleProvider(slug: String) {
        val order = if (slug in providerOrder) providerOrder - slug else providerOrder + slug
        store.update { it.copy(settings = it.settings.copy(llmProviders = order)) }
    }

    /** Instruksjonene modellen får, slik de er nå. */
    fun prompt(): String = VoicePrompt.build(store.data.settings.personality, now())

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isStreaming) return
        if (!store.hasOpenRouterKey) {
            message = "Legg inn en OpenRouter-nøkkel i innstillingene først."
            return
        }
        message = null
        messages = messages + LlmMessage(Role.User, trimmed)
        answer = StringBuilder()
        sentModelName = modelName(modelId)
        provider = null
        firstTokenMillis = null
        firstSentenceMillis = null
        usage = null
        draft = ""
        bridge.openRouterChat(store.openRouterKey, requestBody(), this)
    }

    /** Stopper svaret. Det som er kommet så langt, blir stående uten målinger. */
    fun stop() {
        bridge.openRouterCancelChat()
        val partial = answer.toString().trim()
        if (partial.isNotEmpty()) messages = messages + LlmMessage(Role.Kompis, partial)
        draft = null
    }

    fun clear() {
        if (isStreaming) stop()
        messages = emptyList()
        message = null
    }

    /** Forespørselen til `chat/completions`, med hele samtalen. */
    internal fun requestBody(): String = LlmRequest.body(
        modelId = modelId,
        providers = providerOrder,
        model = models.firstOrNull { it.id == modelId },
        prompt = prompt(),
        messages = messages.map { it.role to it.text },
    )

    // ChatListener, kalles fra Swift.

    override fun onEvent(dataJson: String, millis: Long) {
        if (!isStreaming) return
        val chunk = parseChatChunk(dataJson) ?: return
        chunk.error?.let { error ->
            bridge.openRouterCancelChat()
            onError(error)
            return
        }
        chunk.provider?.let { provider = it }
        chunk.usage?.let { usage = it }

        val content = chunk.content
        if (content.isEmpty()) return
        if (firstTokenMillis == null) firstTokenMillis = millis
        answer.append(content)
        if (firstSentenceMillis == null && Sentences.hasComplete(answer.toString())) firstSentenceMillis = millis
        draft = answer.toString().trimStart()
    }

    override fun onDone(millis: Long) {
        if (!isStreaming) return
        val text = answer.toString().trim()
        val usage = usage
        val stats = LlmStats(
            modelName = sentModelName,
            provider = provider,
            firstTokenMillis = firstTokenMillis,
            // Et svar på én setning uten mellomrom etter er klart når strømmen er ferdig.
            firstSentenceMillis = firstSentenceMillis ?: firstTokenMillis?.let { millis },
            totalMillis = millis,
            promptTokens = usage?.int("prompt_tokens"),
            cachedTokens = usage?.get("prompt_tokens_details")?.jsonObjectOrNull()?.int("cached_tokens"),
            completionTokens = usage?.int("completion_tokens"),
            reasoningTokens = usage?.get("completion_tokens_details")?.jsonObjectOrNull()?.int("reasoning_tokens"),
            costUsd = usage?.get("cost")?.let { (it as? JsonPrimitive)?.doubleOrNull },
        )
        messages = messages + LlmMessage(Role.Kompis, text.ifEmpty { "(tomt svar)" }, stats)
        draft = null
    }

    override fun onError(message: String) {
        draft = null
        this.message = message
    }
}
