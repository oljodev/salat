package no.jodal.salat.apps.kompis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.jodal.salat.NativeBridge
import no.jodal.salat.SttListener

/**
 * Språket modellen får beskjed om. [id] er det som lagres. [code] = `null` lar modellen finne ut av det
 * selv. [allowed] er språkene man kan snakke, når modellen finner språket selv: hører den et annet språk,
 * er det nesten alltid norsk som ble feilhørt, så opptaket kjøres på nytt som det første språket.
 */
enum class SttLanguage(val id: String, val code: String?, val label: String, val allowed: List<String>? = null) {
    Auto("", null, "Auto"),
    Limited("auto:no,en,es", null, "Auto: norsk, engelsk, spansk", allowed = listOf("no", "en", "es")),
    Norwegian("no", "no", "Norsk"),
    English("en", "en", "Engelsk"),
    Spanish("es", "es", "Spansk"),
    ;

    companion object {
        fun byId(id: String): SttLanguage = entries.firstOrNull { it.id == id } ?: Norwegian
    }
}

/** Det Swift trenger for å sende et opptak. Sendes som JSON. */
@Serializable
data class SttConfig(
    val apiKey: String,
    val model: String,
    /** ISO 639-1, eller `null` for å la modellen finne språket. */
    val language: String?,
    /** Be om svar med språket modellen hørte, så det kan sjekkes. */
    val verbose: Boolean = false,
)

/** Innstillingene for et opptak på [language]. */
internal fun SttLanguage.config(apiKey: String, model: String) = SttConfig(apiKey, model, code, verbose = allowed != null)

/** Sjekker om språket modellen hørte, er et av de man kan snakke. */
internal object SttLanguageCheck {
    /** Språket (tokode) opptaket skal sendes på nytt med, eller `null` hvis svaret kan brukes som det er. */
    fun retryWith(language: SttLanguage, responseJson: String): String? {
        val allowed = language.allowed ?: return null
        val detected = OpenRouter.parseDetectedLanguage(responseJson) ?: return null
        return if (OpenRouter.normalizeLanguage(detected) in allowed) null else allowed.first()
    }
}

/** Ett forsøk: hva modellen hørte, og hvor lang tid det tok. */
data class SttResult(
    val text: String,
    val modelId: String,
    val modelName: String,
    val language: SttLanguage,
    val audioSeconds: Double,
    val uploadBytes: Int,
    val millis: Long,
    /** Hva forsøket kostet i dollar, hvis OpenRouter sa det. */
    val costUsd: Double?,
    /** Hva som skjedde med språket, f.eks. «Oppdaget svensk, prøvde igjen som norsk». */
    val note: String? = null,
)

/**
 * Testen av tale til tekst: hold inne, snakk, slipp, og se teksten og hvor lang tid det tok. Et kort
 * trykk starter opptaket i stedet, og et trykk til sender det.
 */
class SttTest(
    private val store: KompisStore,
    private val bridge: NativeBridge,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : SttListener {
    var level by mutableStateOf(0f)
        private set

    var isRecording by mutableStateOf(false)
        private set

    /** Opptaket startet med et kort trykk, og stoppes med et trykk til i stedet for å slippe. */
    var isTapMode by mutableStateOf(false)
        private set

    /** Et opptak er sendt, og svaret har ikke kommet. */
    var isWaiting by mutableStateOf(false)
        private set

    /** Forsøkene, det nyeste først. */
    var results by mutableStateOf(emptyList<SttResult>())
        private set

    var message by mutableStateOf<String?>(null)
        private set

    /** Mikrofonen som brukes, f.eks. «iPhone-mikrofon» eller «AirPods Pro». */
    var microphone by mutableStateOf<String?>(null)
        private set

    /** Hvorfor mikrofonen ikke virker. Blir stående til mikrofonen virker igjen. */
    var microphoneError by mutableStateOf<String?>(null)
        private set

    /** Modellene for tale til tekst hos OpenRouter, de nyeste først. */
    var models by mutableStateOf(cachedModels)
        private set

    var isLoadingModels by mutableStateOf(false)
        private set

    var modelsError by mutableStateOf<String?>(null)
        private set

    val modelId: String get() = store.data.settings.sttModel
    val language: SttLanguage get() = SttLanguage.byId(store.data.settings.sttLanguage)

    /** Navnet på modellen, f.eks. «Voxtral Mini Transcribe 3», eller id-en før listen er hentet. */
    fun modelName(id: String): String = models.firstOrNull { it.id == id }?.shortName ?: id.substringAfter('/')

    /** Modellen og språket for opptaket som er sendt. */
    private var pending: Pair<String, SttLanguage>? = null

    // Når språket skal velges blant noen få, og modellen hørte et annet: opptaket sendes på nytt.
    private var releasedAt = 0L
    private var detected: String? = null
    private var retriedAs: String? = null

    fun open() = bridge.kompisSttOpen(this)

    fun close() {
        bridge.kompisSttClose()
        isRecording = false
        isTapMode = false
        level = 0f
    }

    /** Henter modellene på nytt fra OpenRouter. */
    fun loadModels() {
        if (!store.hasOpenRouterKey || isLoadingModels) return
        isLoadingModels = true
        modelsError = null
        bridge.openRouterGet(OpenRouter.SttModelsPath, store.openRouterKey) { body, error ->
            isLoadingModels = false
            val parsed = body?.let { runCatching { OpenRouter.parseModels(it) }.getOrNull() }
            if (parsed == null) {
                modelsError = error ?: "Forsto ikke modellisten fra OpenRouter."
            } else {
                models = parsed
                cachedModels = parsed
            }
        }
    }

    fun selectModel(id: String) = store.update { it.copy(settings = it.settings.copy(sttModel = id)) }

    fun selectLanguage(language: SttLanguage) =
        store.update { it.copy(settings = it.settings.copy(sttLanguage = language.id)) }

    fun startRecording() {
        if (isRecording || isWaiting) return
        if (!store.hasOpenRouterKey) {
            message = "Legg inn en OpenRouter-nøkkel i innstillingene først."
            return
        }
        message = null
        isRecording = true
        isTapMode = false
        bridge.kompisSttStartRecording()
    }

    /** Knappen ble bare trykket kort, eller iOS avbrøt trykket: fortsett til neste trykk. */
    fun switchToTapMode() {
        if (isRecording) isTapMode = true
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        isTapMode = false
        isWaiting = true
        pending = modelId to language
        releasedAt = clock()
        detected = null
        retriedAs = null
        val config = language.config(store.openRouterKey, modelId)
        bridge.kompisSttStopRecording(json.encodeToString(SttConfig.serializer(), config))
    }

    /** Gjennomsnittlig tid for modellen [id], eller `null` hvis den ikke er testet. */
    fun averageMillis(id: String): Long? =
        results.filter { it.modelId == id }.takeIf { it.isNotEmpty() }?.let { r -> r.sumOf { it.millis } / r.size }

    fun attempts(id: String): Int = results.count { it.modelId == id }

    // SttListener, kalles fra Swift.

    override fun onLevel(level: Float) {
        this.level = level
    }

    override fun onMicrophone(name: String) {
        microphone = name
        microphoneError = null
    }

    override fun onMicrophoneError(message: String) {
        microphoneError = message
    }

    override fun onTranscribed(responseJson: String, audioSeconds: Double, uploadBytes: Int, millis: Long) {
        val (sentModel, sentLanguage) = pending ?: (modelId to language)
        if (retriedAs == null) {
            SttLanguageCheck.retryWith(sentLanguage, responseJson)?.let { code ->
                detected = OpenRouter.parseDetectedLanguage(responseJson)
                retriedAs = code
                bridge.kompisSttResend(json.encodeToString(SttConfig.serializer(), SttConfig(store.openRouterKey, sentModel, code)))
                return
            }
        }
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
                modelId = sentModel,
                modelName = modelName(sentModel),
                language = sentLanguage,
                audioSeconds = audioSeconds,
                uploadBytes = uploadBytes,
                millis = if (retriedAs != null) clock() - releasedAt else millis,
                costUsd = response.usage?.cost,
                note = languageNote(sentLanguage, sentModel, OpenRouter.parseDetectedLanguage(responseJson)),
            ),
        ) + results
    }

    override fun onError(message: String) {
        pending = null
        isRecording = false
        isTapMode = false
        isWaiting = false
        this.message = message
    }

    /** Forklarer språkvalget når modellen fant språket selv. */
    private fun languageNote(language: SttLanguage, model: String, heard: String?): String? {
        val retry = retriedAs
        return when {
            language.allowed == null -> null
            retry != null -> "Oppdaget ${detected?.let(OpenRouter::languageName) ?: "et annet språk"}, prøvde igjen som ${OpenRouter.languageName(retry)}"
            heard != null -> "Oppdaget ${OpenRouter.languageName(heard)}"
            else -> "${modelName(model)} oppgir ikke språket, så auto kan ikke begrenses med denne modellen"
        }
    }

    @Serializable
    private data class SttResponse(val text: String = "", val usage: Usage? = null)

    @Serializable
    private data class Usage(val cost: Double? = null)

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Modellene fra forrige gang, så listen er der med en gang. */
        var cachedModels = emptyList<OpenRouterModel>()
    }
}
