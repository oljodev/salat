package no.jodal.salat.apps.kompis

import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import no.jodal.salat.ChatListener
import no.jodal.salat.LiveListener
import no.jodal.salat.NativeBridge
import no.jodal.salat.SpeechListener
import no.jodal.salat.SttListener

/** Hvor raskt en tur gikk. Tidene er fra man slapp knappen, om ikke noe annet står. */
data class TurnTimings(
    /** Tale til tekst: fra opptaket ble sendt til teksten kom. */
    val sttMillis: Long,
    /** Språkmodellen: fra forespørselen ble sendt til første hele setning var klar. */
    val sentenceMillis: Long?,
    /** Tekst til tale: fra første setning ble sendt til første lyd kom. */
    val voiceMillis: Long?,
    /** Fra man slapp knappen til Kompis begynte å snakke. */
    val heardAfterMillis: Long?,
    /** Fra man slapp knappen til Kompis var ferdig. */
    val totalMillis: Long? = null,
)

/** Det samtalen må gi videre. Samtalen får de samme hendelsene som fra Gemini Live. */
interface PipelineHost : LiveListener {
    /** Noe gikk galt i denne turen, men samtalen fortsetter. */
    fun onNotice(message: String)

    fun onTimings(timings: TurnTimings)
}

/**
 * En samtale med Kompis gjennom OpenRouter: hold inne for å snakke, så blir opptaket til tekst, en
 * språkmodell svarer, og svaret leses opp setning for setning mens det skrives.
 *
 * Swift gjør lyd og nettverk. Her ligger rekkefølgen, instruksjonene, minnet og målingene.
 */
class OpenRouterPipeline(
    private val store: KompisStore,
    private val bridge: NativeBridge,
    private val host: PipelineHost,
    private val now: () -> LocalDateTime = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) },
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private enum class Phase { Opening, Idle, Recording, Transcribing, Thinking, Speaking, Closed }

    private var phase = Phase.Closed

    /** Samtalen så langt, som språkmodellen får den. */
    private val turns = mutableListOf<Pair<Role, String>>()

    // Svaret som kommer nå.
    private val buffer = StringBuilder()
    private val sentences = mutableListOf<String>()
    private var startedSentences = 0
    private var chatDone = false

    // Målinger.
    private var releasedAt = 0L
    private var sttMillis = 0L
    private var sttLanguage = SttLanguage.Norwegian
    private var sttRetried = false
    private var chatSentAt = 0L
    private var sentenceMillis: Long? = null
    private var voiceMillis: Long? = null
    private var heardAfterMillis: Long? = null

    private val settings get() = store.data.settings

    /** Om en tur er i gang: Kompis tenker eller snakker. */
    val isAnswering: Boolean get() = phase == Phase.Thinking || phase == Phase.Speaking

    fun start(history: List<Message>) {
        turns.clear()
        turns += history.takeLast(MaxHistory).map { it.role to it.text }
        phase = Phase.Opening
        host.onStatus("connecting")
        bridge.kompisConversationOpen(stt)
        bridge.speechStart(settings.ttsSampleRates[settings.ttsModel] ?: 0, speech)
        loadModelsIfNeeded()
    }

    fun stop() {
        if (phase == Phase.Closed) return
        phase = Phase.Closed
        bridge.openRouterCancelChat()
        bridge.speechStop()
        bridge.kompisConversationClose()
    }

    /** Knappen trykkes. Avbryter Kompis hvis den snakker, og begynner å ta opp. */
    fun talkStart() {
        when (phase) {
            Phase.Opening, Phase.Recording, Phase.Transcribing, Phase.Closed -> return
            Phase.Thinking, Phase.Speaking -> interrupt()
            Phase.Idle -> Unit
        }
        if (!store.hasOpenRouterKey) {
            host.onNotice("Legg inn en OpenRouter-nøkkel i innstillingene først.")
            return
        }
        phase = Phase.Recording
        host.onStatus("recording")
        bridge.kompisSttStartRecording()
    }

    /** Knappen slippes, eller trykkes én gang til. Sender opptaket. */
    fun talkStop() {
        if (phase != Phase.Recording) return
        phase = Phase.Transcribing
        releasedAt = clock()
        host.onStatus("thinking")
        sttLanguage = SttLanguage.byId(settings.sttLanguage)
        sttRetried = false
        bridge.kompisSttStopRecording(
            json.encodeToString(SttConfig.serializer(), sttLanguage.config(store.openRouterKey, settings.sttModel)),
        )
    }

    // Tale til tekst.

    private val stt = object : SttListener {
        override fun onLevel(level: Float) {
            if (phase == Phase.Recording) host.onLevel(level)
        }

        override fun onMicrophone(name: String) {
            if (phase == Phase.Closed) return
            host.onAudioRoute(name)
            if (phase == Phase.Opening) {
                phase = Phase.Idle
                host.onStatus("listening")
            }
        }

        override fun onMicrophoneError(message: String) {
            if (phase == Phase.Closed) return
            // Uten mikrofon er det ingenting å snakke i.
            stop()
            host.onError(message)
        }

        override fun onTranscribed(responseJson: String, audioSeconds: Double, uploadBytes: Int, millis: Long) {
            if (phase != Phase.Transcribing) return
            if (!sttRetried) {
                // Hørte modellen et språk man ikke snakker, er det feilhørt norsk. Prøv igjen som norsk.
                SttLanguageCheck.retryWith(sttLanguage, responseJson)?.let { code ->
                    sttRetried = true
                    bridge.kompisSttResend(json.encodeToString(SttConfig.serializer(), SttConfig(store.openRouterKey, settings.sttModel, code)))
                    return
                }
            }
            sttMillis = if (sttRetried) clock() - releasedAt else millis
            val text = OpenRouter.parseTranscript(responseJson)
            when {
                text == null -> backToListening("Forsto ikke svaret fra OpenRouter.")
                text.isEmpty() -> backToListening("Hørte ikke noe. Prøv igjen.")
                else -> {
                    host.onUserText(text)
                    turns += Role.User to text
                    answer()
                }
            }
        }

        override fun onError(message: String) {
            if (phase == Phase.Transcribing || phase == Phase.Recording) backToListening(message)
        }
    }

    // Språkmodellen.

    private fun answer() {
        phase = Phase.Thinking
        host.onStatus("thinking")
        buffer.clear()
        sentences.clear()
        startedSentences = 0
        chatDone = false
        sentenceMillis = null
        voiceMillis = null
        heardAfterMillis = null
        chatSentAt = clock()
        val body = LlmRequest.body(
            modelId = settings.llmModel,
            providers = settings.llmProviders,
            model = ModelCache.llm.firstOrNull { it.id == settings.llmModel },
            prompt = VoicePrompt.build(settings.personality, now()),
            messages = turns.takeLast(MaxHistory),
        )
        bridge.openRouterChat(store.openRouterKey, body, chat)
    }

    private val chat = object : ChatListener {
        override fun onEvent(dataJson: String, millis: Long) {
            if (!isAnswering || chatDone) return
            val chunk = parseChatChunk(dataJson) ?: return
            if (chunk.error != null) {
                abort(chunk.error)
                return
            }
            if (chunk.content.isEmpty()) return
            buffer.append(chunk.content)
            val (complete, rest) = Sentences.split(buffer.toString())
            complete.forEach { enqueue(it) }
            var remaining = rest
            // Lange setninger uten punktum deles ved et komma, så stemmen kan begynne.
            while (true) {
                val (clause, after) = Sentences.cutLongClause(remaining) ?: break
                enqueue(clause)
                remaining = after
            }
            buffer.clear()
            buffer.append(remaining)
        }

        override fun onDone(millis: Long) {
            if (!isAnswering || chatDone) return
            chatDone = true
            enqueue(buffer.toString())
            buffer.clear()
            if (sentences.isEmpty()) {
                backToListening("Kompis ga ikke noe svar.")
            } else {
                bridge.speechFinish()
            }
        }

        override fun onError(message: String) {
            if (isAnswering) abort(message)
        }
    }

    // Tekst til tale.

    /** Sender [raw] til stemmen, hvis det er noe å lese. */
    private fun enqueue(raw: String) {
        val text = Sentences.speakable(raw)
        if (text.isEmpty()) return
        if (sentences.isEmpty()) sentenceMillis = clock() - chatSentAt
        val id = "s${sentences.size}"
        sentences += text
        bridge.speechEnqueue(id, store.openRouterKey, speechBody(text))
    }

    private fun speechBody(text: String): String = buildJsonObject {
        put("model", settings.ttsModel)
        put("input", text)
        voice()?.let { put("voice", it) }
        settings.ttsInstructions.trim().takeIf { it.isNotEmpty() }?.let { put("instructions", it) }
        put("response_format", "pcm")
    }.toString()

    /** Stemmen som er valgt for modellen, ellers den første, de norske først. */
    private fun voice(): String? = settings.ttsVoices[settings.ttsModel]
        ?: ModelCache.tts.firstOrNull { it.id == settings.ttsModel }?.voices?.sortedBy { !isNorwegianVoice(it) }?.firstOrNull()

    private val speech = object : SpeechListener {
        override fun onFirstAudio(id: String, millis: Long) {
            if (id == "s0") voiceMillis = millis
        }

        override fun onItemStarted(id: String) {
            if (!isAnswering) return
            val index = id.removePrefix("s").toIntOrNull()?.takeIf { it in sentences.indices } ?: return
            startedSentences = maxOf(startedSentences, index + 1)
            if (phase != Phase.Speaking) {
                phase = Phase.Speaking
                host.onStatus("speaking")
            }
            host.onKompisText(if (index == 0) sentences[index] else " ${sentences[index]}")
            if (index == 0) {
                heardAfterMillis = clock() - releasedAt
                host.onTimings(timings())
            }
        }

        override fun onItemFinished(id: String) = Unit

        override fun onFinished() {
            if (!isAnswering) return
            turns += Role.Kompis to sentences.joinToString(" ")
            phase = Phase.Idle
            host.onTurnComplete()
            host.onStatus("listening")
            host.onTimings(timings().copy(totalMillis = clock() - releasedAt))
        }

        override fun onLevel(level: Float) {
            if (phase == Phase.Speaking) host.onLevel(level)
        }

        override fun onError(message: String) {
            if (isAnswering) abort("Stemmen feilet: $message")
        }
    }

    private fun timings() = TurnTimings(sttMillis, sentenceMillis, voiceMillis, heardAfterMillis)

    // Avbrudd og feil.

    /** Stopper svaret midt i. Det som er sagt så langt, blir med i samtalen. */
    private fun interrupt() {
        bridge.openRouterCancelChat()
        bridge.speechStop()
        keepWhatWasSaid()
        phase = Phase.Idle
        host.onInterrupted()
    }

    /** Noe gikk galt midt i svaret. Det som er sagt, blir stående, og samtalen fortsetter. */
    private fun abort(message: String) {
        bridge.openRouterCancelChat()
        bridge.speechStop()
        keepWhatWasSaid()
        phase = Phase.Idle
        host.onInterrupted()
        host.onStatus("listening")
        host.onNotice(message)
    }

    private fun backToListening(message: String) {
        phase = Phase.Idle
        host.onStatus("listening")
        host.onNotice(message)
    }

    private fun keepWhatWasSaid() {
        val said = sentences.take(startedSentences).joinToString(" ")
        if (said.isNotEmpty()) turns += Role.Kompis to said
    }

    // Modellene.

    /** Henter modellisten, så språkmodellen får riktig tenkeinnstilling og stemmen kan velges. */
    private fun loadModelsIfNeeded() {
        if (!store.hasOpenRouterKey) return
        if (ModelCache.llm.isEmpty()) {
            bridge.openRouterGet(OpenRouter.LlmModelsPath, store.openRouterKey) { body, _ ->
                body?.let { runCatching { OpenRouter.parseModels(it) }.getOrNull() }?.let { ModelCache.llm = it }
            }
        }
        if (ModelCache.tts.isEmpty()) {
            bridge.openRouterGet(OpenRouter.TtsModelsPath, store.openRouterKey) { body, _ ->
                body?.let { runCatching { OpenRouter.parseModels(it) }.getOrNull() }?.let { ModelCache.tts = it }
            }
        }
    }

    internal companion object {
        /** Hvor mange tidligere meldinger språkmodellen får med. */
        const val MaxHistory = 40

        private val json = Json { encodeDefaults = true }
    }
}

/** F.eks. «Tale til tekst 210 ms · Språkmodell 380 ms · Stemme 240 ms · Hørt etter 940 ms». */
fun formatTimings(timings: TurnTimings): String = listOfNotNull(
    "Tale til tekst ${timings.sttMillis} ms",
    timings.sentenceMillis?.let { "Språkmodell $it ms" },
    timings.voiceMillis?.let { "Stemme $it ms" },
    timings.heardAfterMillis?.let { "Hørt etter $it ms" },
    timings.totalMillis?.let { "ferdig ${formatSeconds(it / 1000.0)}" },
).joinToString("  ·  ")
