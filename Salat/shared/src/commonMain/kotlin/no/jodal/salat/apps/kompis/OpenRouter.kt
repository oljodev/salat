package no.jodal.salat.apps.kompis

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** En modell hos OpenRouter. [name] er f.eks. «Mistral: Voxtral Mini Transcribe 3». */
data class OpenRouterModel(val id: String, val name: String, val createdAtSeconds: Long) {
    /** Navnet uten firmaet foran, f.eks. «Voxtral Mini Transcribe 3». */
    val shortName: String get() = name.substringAfter(": ", name)

    /** Firmaet bak, f.eks. «Mistral». */
    val maker: String? get() = name.substringBefore(": ", "").ifEmpty { null }
}

/** Modellen testen av tale til tekst starter med. */
const val DefaultSttModel = "mistralai/voxtral-mini-transcribe-3-0"

object OpenRouter {
    /** Modellene som gjør tale til tekst, filtrert etter innstillingene til nøkkelen hos OpenRouter. */
    const val SttModelsPath = "models/user?output_modalities=transcription"

    private val json = Json { ignoreUnknownKeys = true }

    /** Modellene i svaret fra `/models`, de nyeste først. */
    fun parseModels(body: String): List<OpenRouterModel> =
        json.decodeFromString(ModelList.serializer(), body).data
            .map { OpenRouterModel(it.id, it.name.ifBlank { it.id }, it.created) }
            .sortedByDescending { it.createdAtSeconds }

    @Serializable
    private data class ModelList(val data: List<ModelInfo>)

    @Serializable
    private data class ModelInfo(val id: String, val name: String = "", val created: Long = 0)
}
