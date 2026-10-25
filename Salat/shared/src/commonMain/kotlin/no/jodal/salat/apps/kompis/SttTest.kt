package no.jodal.salat.apps.kompis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.jodal.salat.NativeBridge
import no.jodal.salat.SttListener

/** Modellene for tale til tekst som kan testes. [id] er navnet hos OpenRouter. */
enum class SttModel(val id: String, val label: String) {
    VoxtralMini("mistralai/voxtral-mini-transcribe-3-0", "Voxtral Mini"),
    VoxtralSmall("mistralai/voxtral-small-transcribe-3-0", "Voxtral Small"),
    WhisperTurbo("openai/whisper-large-v3-turbo", "Whisper Turbo"),
    GptMini("openai/gpt-4o-mini-transcribe", "GPT-4o Mini"),
    ;

    companion object {
        fun byId(id: String): SttModel = entries.firstOrNull { it.id == id } ?: VoxtralMini
    }
}

/** Språket modellen får beskjed om. [code] = `null` lar modellen finne ut av det selv. */
enum class SttLanguage(val code: String?, val label: String) {
    Auto(null, "Auto"),
    Norwegian("no", "Norsk"),
    English("en", "Engelsk"),
    Spanish("es", "Spansk"),
    ;

    companion object {
        fun byCode(code: String): SttLanguage = entries.firstOrNull { it.code == code.ifEmpty { null } } ?: Norwegian
    }
}

/** Det Swift trenger for å sende et opptak. Sendes som JSON. */
@Serializable
data class SttConfig(
    val apiKey: String,
    val model: String,
    /** ISO 639-1, eller `null` for å la modellen finne språket. */
    val language: String?,
)

/** Ett forsøk: hva modellen hørte, og hvor lang tid det tok. */
data class SttResult(
    val text: String,
    val model: SttModel,
    val language: SttLanguage,
    val audioSeconds: Double,
    val uploadBytes: Int,
    val millis: Long,
    /** Hva forsøket kostet i dollar, hvis OpenRouter sa det. */
    val costUsd: Double?,
)

/** Testen av tale til tekst: hold inne, snakk, slipp, og se teksten og hvor lang tid det tok. */
class SttTest(private val store: KompisStore, private val bridge: NativeBridge) : SttListener {
    var level by mutableStateOf(0f)
        private set

    var isRecording by mutableStateOf(false)
        private set

    /** Et opptak er sendt, og svaret har ikke kommet. */
    var isWaiting by mutableStateOf(false)
        private set

    /** Forsøkene, det nyeste først. */
    var results by mutableStateOf(emptyList<SttResult>())
        private set

    var message by mutableStateOf<String?>(null)
        private set

    val model: SttModel get() = SttModel.byId(store.data.settings.sttModel)
    val language: SttLanguage get() = SttLanguage.byCode(store.data.settings.sttLanguage)

    /** Modellen og språket for opptaket som er sendt. */
    private var pending: Pair<SttModel, SttLanguage>? = null

    fun open() = bridge.kompisSttOpen(this)

    fun close() {
        bridge.kompisSttClose()
        isRecording = false
        level = 0f
    }

    fun selectModel(model: SttModel) = store.update { it.copy(settings = it.settings.copy(sttModel = model.id)) }

    fun selectLanguage(language: SttLanguage) =
        store.update { it.copy(settings = it.settings.copy(sttLanguage = language.code.orEmpty())) }

    fun startRecording() {
        if (isRecording || isWaiting) return
        if (!store.hasOpenRouterKey) {
            message = "Legg inn en OpenRouter-nøkkel i innstillingene først."
            return
        }
        message = null
        isRecording = true
        bridge.kompisSttStartRecording()
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        isWaiting = true
        pending = model to language
        val config = SttConfig(apiKey = store.openRouterKey, model = model.id, language = language.code)
        bridge.kompisSttStopRecording(json.encodeToString(SttConfig.serializer(), config))
    }

    /** Gjennomsnittlig tid for [model], eller `null` hvis den ikke er testet. */
    fun averageMillis(model: SttModel): Long? =
        results.filter { it.model == model }.takeIf { it.isNotEmpty() }?.let { r -> r.sumOf { it.millis } / r.size }

    // SttListener, kalles fra Swift.

    override fun onLevel(level: Float) {
        this.level = level
    }

    override fun onTranscribed(responseJson: String, audioSeconds: Double, uploadBytes: Int, millis: Long) {
        val (sentModel, sentLanguage) = pending ?: (model to language)
        pending = null
        isWaiting = false
        val response = runCatching { json.decodeFromString(SttResponse.serializer(), responseJson) }.getOrNull()
        if (response == null) {
            message = "Forsto ikke svaret fra OpenRouter."
            return
        }
        results = listOf(
            SttResult(
                text = response.text.trim(),
                model = sentModel,
                language = sentLanguage,
                audioSeconds = audioSeconds,
                uploadBytes = uploadBytes,
                millis = millis,
                costUsd = response.usage?.cost,
            ),
        ) + results
    }

    override fun onError(message: String) {
        pending = null
        isRecording = false
        isWaiting = false
        this.message = message
    }

    @Serializable
    private data class SttResponse(val text: String = "", val usage: Usage? = null)

    @Serializable
    private data class Usage(val cost: Double? = null)

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
