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

/** Språkmodellen Kompis starter med. Valgt for prisen. */
const val DefaultLlmModel = "deepseek/deepseek-v4-flash-0731"

/** Leverandørene for [DefaultLlmModel], i rekkefølge. De er raske og har samme pris. */
val DefaultLlmProviders = listOf("wafer", "baseten")

object OpenRouter {
    /** Modellene som gjør tale til tekst, filtrert etter innstillingene til nøkkelen hos OpenRouter. */
    const val SttModelsPath = "models/user?output_modalities=transcription"

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
                )
            }
            .sortedByDescending { it.createdAtSeconds }

    /** Leverandørene i svaret fra `/models/{id}/endpoints`, én gang hver, i rekkefølgen OpenRouter gir dem. */
    fun parseProviders(body: String): List<OpenRouterProvider> =
        json.decodeFromString(EndpointsResponse.serializer(), body).data.endpoints
            .map { OpenRouterProvider(slug = (it.tag ?: it.provider_name).substringBefore('/').lowercase(), name = it.provider_name) }
            .distinctBy { it.slug }

    @Serializable
    private data class ModelList(val data: List<ModelInfo>)

    @Serializable
    private data class ModelInfo(val id: String, val name: String = "", val created: Long = 0, val reasoning: ReasoningInfo? = null)

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
