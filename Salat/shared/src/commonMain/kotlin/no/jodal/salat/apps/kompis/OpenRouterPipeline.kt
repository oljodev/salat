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

    // Håndfri modus: mikrofonen står på, og [voice] hører når man snakker.
    private val voice = VoiceActivity(clock)

    /** Opptaket startet av seg selv, og stopper når det blir stille. */
    private var autoRecording = false

    /** Mikrofonen har ekkodemping. Uten den kan Kompis ikke avbrytes ved å snakke, for da hører den seg selv. */
    private var echoCancelled = true

    /** Samtalen så langt, som språkmodellen får den. */
    private val turns = mutableListOf<Pair<Role, String>>()

    // Svaret som kommer nå. [buffer] er det som skal sies, men ikke er en hel setning ennå.
    private var stream = SayStream()
    private var understoodHandled = false
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
    private val handsFree get() = settings.handsFree

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
        startRecording(auto = false)
    }

    private fun startRecording(auto: Boolean) {
        if (!store.hasOpenRouterKey) {
            host.onNotice("Legg inn en OpenRouter-nøkkel i innstillingene først.")
            return
        }
        autoRecording = auto || handsFree
        phase = Phase.Recording
        host.onStatus("recording")
        bridge.kompisSttStartRecording()
    }

    /** Knappen slippes, eller trykkes én gang til. Sender opptaket. */
    fun talkStop() = stopRecording(silentFor = 0)

    /**
     * Et trykk i håndfri modus: sender det som tas opp med en gang, avbryter Kompis, eller begynner å ta opp.
     */
    fun tap() {
        when (phase) {
            Phase.Recording -> talkStop()
            Phase.Thinking, Phase.Speaking -> {
                interrupt()
                host.onStatus("listening")
            }
            Phase.Idle -> talkStart()
            Phase.Opening, Phase.Transcribing, Phase.Closed -> Unit
        }
    }

    /** Bytter mellom håndfri og knapp. Et opptak som pågår, sendes. */
    fun handsFreeChanged() {
        voice.reset()
        if (phase == Phase.Recording && autoRecording && !handsFree) talkStop()
    }

    /** [silentFor]: hvor lenge det var stille før opptaket ble stoppet. Tiden regnes fra man sluttet å snakke. */
    private fun stopRecording(silentFor: Long) {
        if (phase != Phase.Recording) return
        phase = Phase.Transcribing
        voice.reset()
        releasedAt = clock() - silentFor
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
            if (handsFree) listenHandsFree(level)
            if (phase == Phase.Recording || (handsFree && phase == Phase.Idle)) host.onLevel(level)
        }

        override fun onMicrophone(name: String) {
            if (phase == Phase.Closed) return
            echoCancelled = !name.contains("uten ekkodemping")
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
                // Håndfri hører av og til en hoste eller en dør, og det er ikke noe å si fra om.
                text.isEmpty() -> backToListening(if (handsFree) null else "Hørte ikke noe. Prøv igjen.")
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

    /** Hører etter tale i håndfri modus: starter opptaket, stopper det når det blir stille, og avbryter Kompis. */
    private fun listenHandsFree(level: Float) {
        val answering = phase == Phase.Thinking || phase == Phase.Speaking
        when (phase) {
            Phase.Idle, Phase.Recording -> Unit
            // Uten ekkodemping hører Kompis seg selv, så da avbrytes den bare med et trykk.
            Phase.Thinking, Phase.Speaking -> if (!echoCancelled) return
            else -> return
        }
        when (voice.update(level, strict = answering)) {
            VoiceActivity.Event.None -> Unit
            VoiceActivity.Event.SpeechStarted -> when (phase) {
                Phase.Idle -> startRecording(auto = true)
                Phase.Thinking, Phase.Speaking -> {
                    interrupt()
                    startRecording(auto = true)
                }
                else -> Unit
            }
            VoiceActivity.Event.SpeechEnded -> if (phase == Phase.Recording && autoRecording) stopRecording(silentFor = voice.silenceMillis)
        }
    }

    // Språkmodellen.

    private fun answer() {
        phase = Phase.Thinking
        host.onStatus("thinking")
        stream = SayStream()
        understoodHandled = false
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
            // Modellen svarer med verktøyet say: først hva den tror du sa, så språket, og så det som skal sies.
            val added = stream.add(chunk)
            useUnderstood()
            if (added.isEmpty()) return
            buffer.append(added)
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

    /**
     * Modellen skrev hva den tror brukeren sa, med feilhørte ord rettet. Det brukes i minnet i stedet
     * for det som ble hørt, så «tusen tack» ikke blir stående der som svensk.
     */
    private fun useUnderstood() {
        val understood = stream.understood ?: return
        if (understoodHandled) return
        understoodHandled = true
        val index = turns.indexOfLast { it.first == Role.User }
        if (index < 0 || turns[index].second == understood) return
        host.onDebug("Forstod: $understood  (hørt som: ${turns[index].second})")
        turns[index] = Role.User to understood
    }

    /** Sender [raw] til stemmen, hvis det er noe å lese. */
    private fun enqueue(raw: String) {
        val text = Sentences.speakable(raw)
        if (text.isEmpty()) return
        if (sentences.isEmpty()) sentenceMillis = clock() - chatSentAt
        val id = "s${sentences.size}"
        sentences += text
        bridge.speechEnqueue(id, store.openRouterKey, speechBody(text))
    }

    /** Forespørselen til stemmen, med språket og regien språkmodellen ga. */
    private fun speechBody(text: String): String = buildJsonObject {
        put("model", settings.ttsModel)
        put("input", text)
        voice(stream.language)?.let { put("voice", it) }
        listOfNotNull(settings.ttsInstructions.trim().ifEmpty { null }, stream.style)
            .takeIf { it.isNotEmpty() }
            ?.let { put("instructions", it.joinToString(". ")) }
        put("response_format", "pcm")
    }.toString()

    /**
     * Stemmen som er valgt for modellen, ellers den første, de norske først. Svarer Kompis på engelsk
     * eller spansk, og modellen har en stemme til språket, brukes den.
     */
    private fun voice(language: String?): String? {
        val voices = ModelCache.tts.firstOrNull { it.id == settings.ttsModel }?.voices.orEmpty().sortedBy { !isNorwegianVoice(it) }
        val selected = settings.ttsVoices[settings.ttsModel] ?: voices.firstOrNull()
        return VoiceChoice.pick(voices, selected, language)
    }

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
            voice.reset()
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

    private fun backToListening(message: String?) {
        phase = Phase.Idle
        voice.reset()
        host.onStatus("listening")
        message?.let(host::onNotice)
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
