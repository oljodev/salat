package no.jodal.salat.apps.kompis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.jodal.salat.NativeBridge
import no.jodal.salat.TtsListener

/** Ett forsøk med tekst til tale. Tidene er fra forespørselen ble sendt, og `null` til de er kjent. */
data class TtsResult(
    val id: String,
    val text: String,
    val modelId: String,
    val modelName: String,
    val voice: String?,
    val firstAudioMillis: Long? = null,
    val totalMillis: Long? = null,
    val audioSeconds: Double? = null,
    val bytes: Int? = null,
    /** F.eks. `pcm 24 kHz` eller `mp3`. */
    val format: String? = null,
    /** Omtrent hva det kostet, ut fra prisen per tegn. */
    val costUsd: Double? = null,
    val error: String? = null,
) {
    val isLoading: Boolean get() = totalMillis == null && error == null
}

/** Ferdige setninger som prøver det som ofte går galt: tall, klokkeslett, engelske ord og andre språk. */
enum class TtsSample(val label: String, val text: String) {
    Greeting("Hilsen", "Hei! Så hyggelig å høre fra deg. Hvordan har dagen din vært så langt?"),
    Numbers("Tall og tid", "Bussen går 08:15 fra Jernbanetorget, og billetten koster 42 kroner. Turen tar 25 minutter."),
    Words("Engelske ord", "Jeg så en video på YouTube om machine learning, og den var faktisk ganske nice."),
    Facts("Fakta", "Visste du at Mjøsa er Norges største innsjø? Den er omtrent hundre og sytten kilometer lang."),
    Feelings("Følelser", "Å nei, så leit å høre! Det høres ut som en skikkelig tung dag. Vil du snakke om det?"),
    English("English", "Sure, I can switch to English. What would you like to talk about?"),
    Spanish("Español", "¡Claro! Podemos hablar en español. ¿Qué tal tu día?"),
}

/** Samplingsfrekvensene man kan velge hvis stemmen høres for lys eller mørk ut. 0 = finn ut selv. */
val TtsSampleRates = listOf(0, 16_000, 22_050, 24_000, 44_100, 48_000)

/** Testen av tekst til tale: velg modell og stemme, les opp en tekst, og se hvor raskt lyden kommer. */
class TtsTest(private val store: KompisStore, private val bridge: NativeBridge) : TtsListener {
    var models by mutableStateOf(cachedModels)
        private set

    var isLoadingModels by mutableStateOf(false)
        private set

    var modelsError by mutableStateOf<String?>(null)
        private set

    /** Forsøkene, det nyeste først. */
    var results by mutableStateOf(emptyList<TtsResult>())
        private set

    /** Forsøket som spilles av nå, eller `null`. */
    var playingId by mutableStateOf<String?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    /** Teksten som skal leses opp. */
    var text by mutableStateOf(TtsSample.Greeting.text)

    private var nextId = 1

    private val settings get() = store.data.settings

    val modelId: String get() = settings.ttsModel

    fun modelName(id: String): String = models.firstOrNull { it.id == id }?.shortName ?: id.substringAfter('/')

    /** Stemmene til modellen, de norske først. */
    val voices: List<String>
        get() = models.firstOrNull { it.id == modelId }?.voices.orEmpty().sortedBy { !isNorwegianVoice(it) }

    /** Den valgte stemmen, eller den første. `null` hvis modellen ikke har en stemmeliste. */
    val voice: String?
        get() = settings.ttsVoices[modelId]?.takeIf { voices.isEmpty() || it in voices } ?: voices.firstOrNull()

    val sampleRate: Int get() = settings.ttsSampleRates[modelId] ?: 0

    val instructions: String get() = settings.ttsInstructions

    val callAudio: Boolean get() = settings.ttsCallAudio

    val isBusy: Boolean get() = results.any { it.isLoading }

    fun open() = bridge.openRouterWarmUp()

    fun close() {
        bridge.ttsClose()
        playingId = null
    }

    fun loadModels() {
        if (!store.hasOpenRouterKey || isLoadingModels) return
        isLoadingModels = true
        modelsError = null
        bridge.openRouterGet(OpenRouter.TtsModelsPath, store.openRouterKey) { body, error ->
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

    fun selectModel(id: String) = updateSettings { it.copy(ttsModel = id) }

    fun selectVoice(voice: String) = updateSettings { it.copy(ttsVoices = it.ttsVoices + (modelId to voice)) }

    fun selectSampleRate(rate: Int) = updateSettings { it.copy(ttsSampleRates = it.ttsSampleRates + (modelId to rate)) }

    fun setInstructions(value: String) = updateSettings { it.copy(ttsInstructions = value) }

    fun setCallAudio(value: Boolean) = updateSettings { it.copy(ttsCallAudio = value) }

    private fun updateSettings(transform: (KompisSettings) -> KompisSettings) =
        store.update { it.copy(settings = transform(it.settings)) }

    /** Leser opp [text] med modellen og stemmen som er valgt. */
    fun speak() {
        val input = text.trim()
        if (input.isEmpty() || isBusy) return
        if (!store.hasOpenRouterKey) {
            message = "Legg inn en OpenRouter-nøkkel i innstillingene først."
            return
        }
        message = null
        val id = "tts-${nextId++}"
        val price = models.firstOrNull { it.id == modelId }?.inputPrice
        results = listOf(
            TtsResult(
                id = id,
                text = input,
                modelId = modelId,
                modelName = modelName(modelId),
                voice = voice,
                costUsd = price?.takeIf { it > 0 }?.let { it * input.length },
            ),
        ) + results
        playingId = id
        bridge.ttsSpeak(id, store.openRouterKey, requestBody(input), sampleRate, callAudio, this)
    }

    fun replay(id: String) {
        playingId = id
        bridge.ttsReplay(id, callAudio, this)
    }

    fun stop() {
        bridge.ttsStop()
        playingId = null
        results = results.map { if (it.isLoading) it.copy(error = "Stoppet.") else it }
    }

    /** Gjennomsnittlig tid til første lyd for modellen [id], eller `null` hvis den ikke er testet. */
    fun averageFirstAudio(id: String): Long? =
        results.mapNotNull { r -> r.firstAudioMillis.takeIf { r.modelId == id } }.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }

    /** Forespørselen til `audio/speech`. Rå lyd (`pcm`) kan spilles av mens den kommer. */
    internal fun requestBody(input: String): String = buildJsonObject {
        put("model", modelId)
        put("input", input)
        voice?.let { put("voice", it) }
        instructions.trim().takeIf { it.isNotEmpty() }?.let { put("instructions", it) }
        put("response_format", "pcm")
    }.toString()

    // TtsListener, kalles fra Swift.

    override fun onFirstAudio(id: String, millis: Long) = updateResult(id) { it.copy(firstAudioMillis = millis) }

    override fun onReceived(id: String, millis: Long, audioSeconds: Double, bytes: Int, format: String) =
        updateResult(id) {
            it.copy(firstAudioMillis = it.firstAudioMillis ?: millis, totalMillis = millis, audioSeconds = audioSeconds, bytes = bytes, format = format)
        }

    override fun onPlaying(id: String, playing: Boolean) {
        if (playing) {
            playingId = id
        } else if (playingId == id) {
            playingId = null
        }
    }

    override fun onError(id: String, message: String) {
        updateResult(id) { it.copy(error = message) }
        if (playingId == id) playingId = null
    }

    private fun updateResult(id: String, transform: (TtsResult) -> TtsResult) {
        results = results.map { if (it.id == id) transform(it) else it }
    }

    private companion object {
        /** Modellene fra forrige gang, så listen er der med en gang. */
        var cachedModels = emptyList<OpenRouterModel>()
    }
}

/** Om stemmen er norsk, ut fra navnet, f.eks. `nb-NO-Grant:MAI-Voice-2.1`. */
fun isNorwegianVoice(voice: String): Boolean {
    val v = voice.lowercase()
    return "nb-no" in v || "nn-no" in v || v.startsWith("no_") || v.startsWith("nb_")
}
