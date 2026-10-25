package no.jodal.salat.apps.kompis

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** En modell hos OpenRouter. [name] er f.eks. «Mistral: Voxtral Mini Transcribe 3». */
data class OpenRouterModel(
    val id: String,
    val name: String,
    val createdAtSeconds: Long,
    /** Hvordan modellen tenker før den svarer, eller `null` hvis den ikke gjør det. */
    val reasoning: ModelReasoning? = null,
    /** Stemmene en modell for tekst til tale har. */
    val voices: List<String> = emptyList(),
    /** Prisen i dollar per enhet inn, f.eks. per tegn for tekst til tale, eller `null` hvis ukjent. */
    val inputPrice: Double? = null,
) {
    /** Navnet uten firmaet foran, f.eks. «Voxtral Mini Transcribe 3». */
    val shortName: String get() = name.substringAfter(": ", name)

    /** Firmaet bak, f.eks. «Mistral». */
    val maker: String? get() = name.substringBefore(": ", "").ifEmpty { null }
}

/** [mandatory]: tenkingen kan ikke slås av. [supportsNone]: den slås av med `effort: "none"`. */
data class ModelReasoning(val mandatory: Boolean, val supportsNone: Boolean)

/** En leverandør som kjører en modell, f.eks. Wafer. [slug] er det OpenRouter vil ha i `provider.order`. */
data class OpenRouterProvider(val slug: String, val name: String)

/** Modellen testen av tale til tekst starter med. */
const val DefaultSttModel = "mistralai/voxtral-mini-transcribe-3-0"

/** Modellen testen av tekst til tale starter med. */
const val DefaultTtsModel = "mistralai/voxtral-mini-tts-3-0"

/** Språkmodellen Kompis starter med. Valgt for prisen. */
const val DefaultLlmModel = "deepseek/deepseek-v4-flash-0731"

/** Leverandørene for [DefaultLlmModel], i rekkefølge. De er raske og har samme pris. */
val DefaultLlmProviders = listOf("wafer", "baseten")

/** Modellene fra forrige gang de ble hentet, så testene og samtalene ikke må vente på listen. */
internal object ModelCache {
    var llm = emptyList<OpenRouterModel>()
    var tts = emptyList<OpenRouterModel>()
}

object OpenRouter {
    /** Modellene som gjør tale til tekst, filtrert etter innstillingene til nøkkelen hos OpenRouter. */
    const val SttModelsPath = "models/user?output_modalities=transcription"

    /** Modellene som gjør tekst til tale, filtrert etter innstillingene til nøkkelen hos OpenRouter. */
    const val TtsModelsPath = "models/user?output_modalities=speech"

    /** Språkmodellene, filtrert etter innstillingene til nøkkelen hos OpenRouter. */
    const val LlmModelsPath = "models/user?output_modalities=text"

    fun endpointsPath(modelId: String) = "models/$modelId/endpoints"

    private val json = Json { ignoreUnknownKeys = true }

    /** Modellene i svaret fra `/models`, de nyeste først. */
    fun parseModels(body: String): List<OpenRouterModel> =
        json.decodeFromString(ModelList.serializer(), body).data
            .map { info ->
                OpenRouterModel(
                    id = info.id,
                    name = info.name.ifBlank { info.id },
                    createdAtSeconds = info.created,
                    reasoning = info.reasoning?.let {
                        ModelReasoning(mandatory = it.mandatory, supportsNone = "none" in it.supported_efforts.orEmpty())
                    },
                    voices = info.supported_voices.orEmpty(),
                    inputPrice = info.pricing?.prompt?.toDoubleOrNull(),
                )
            }
            .sortedByDescending { it.createdAtSeconds }

    /** Teksten i svaret fra `audio/transcriptions`, eller `null` hvis svaret ikke kan leses. */
    fun parseTranscript(body: String): String? =
        runCatching { json.decodeFromString(Transcript.serializer(), body).text.trim() }.getOrNull()

    /** Språket modellen sier den hørte i svaret fra `audio/transcriptions`, hvis det står der. */
    fun parseDetectedLanguage(body: String): String? =
        runCatching { json.decodeFromString(Transcript.serializer(), body).language }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    /** Språket som en tokode, f.eks. «no» for både «no», «nb» og «Norwegian». Andre språk gis som små bokstaver. */
    fun normalizeLanguage(raw: String): String = when (val name = raw.trim().lowercase()) {
        "no", "nb", "nn", "nob", "nno", "norwegian", "norsk", "bokmål", "norwegian bokmål", "norwegian nynorsk" -> "no"
        "en", "eng", "english", "engelsk" -> "en"
        "es", "spa", "spanish", "español", "espanol", "spansk" -> "es"
        else -> name
    }

    /** Språket på norsk, f.eks. «norsk» for «no». Ukjente språk gis som de er. */
    fun languageName(raw: String): String = when (val code = normalizeLanguage(raw)) {
        "no" -> "norsk"
        "en" -> "engelsk"
        "es" -> "spansk"
        "sv" -> "svensk"
        "da" -> "dansk"
        "de" -> "tysk"
        "fr" -> "fransk"
        else -> code
    }

    /** Leverandørene i svaret fra `/models/{id}/endpoints`, én gang hver, i rekkefølgen OpenRouter gir dem. */
    fun parseProviders(body: String): List<OpenRouterProvider> =
        json.decodeFromString(EndpointsResponse.serializer(), body).data.endpoints
            .map { OpenRouterProvider(slug = (it.tag ?: it.provider_name).substringBefore('/').lowercase(), name = it.provider_name) }
            .distinctBy { it.slug }

    @Serializable
    private data class Transcript(val text: String = "", val language: String? = null)

    @Serializable
    private data class ModelList(val data: List<ModelInfo>)

    @Suppress("PropertyName")
    @Serializable
    private data class ModelInfo(
        val id: String,
        val name: String = "",
        val created: Long = 0,
        val reasoning: ReasoningInfo? = null,
        val supported_voices: List<String>? = null,
        val pricing: Pricing? = null,
    )

    @Serializable
    private data class Pricing(val prompt: String? = null)

    @Suppress("PropertyName")
    @Serializable
    private data class ReasoningInfo(val mandatory: Boolean = false, val supported_efforts: List<String>? = null)

    @Serializable
    private data class EndpointsResponse(val data: EndpointsData)

    @Serializable
    private data class EndpointsData(val endpoints: List<EndpointInfo> = emptyList())

    @Suppress("PropertyName")
    @Serializable
    private data class EndpointInfo(val provider_name: String, val tag: String? = null)
}
