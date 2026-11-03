package no.jodal.salat.apps.kompis

import kotlinx.coroutines.*
import no.jodal.salat.apps.kompis.tools.*
import kotlinx.serialization.json.JsonObject
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
@kotlinx.serialization.Serializable
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

    fun onMessageInfo(info: MessageInfo) {}
    fun onToolCard(card: ToolCard) {}
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
    private val tools: KompisTools = KompisTools(listOf(SayTool) + RiseTools(bridge).all),
    private val toolScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
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
    private val turns = mutableListOf<Message>()

    // Svaret som kommer nå. [buffer] er det som skal sies, men ikke er en hel setning ennå.
    private var stream = SayStream()
    private var calls = ToolCallAssembler()
    private var round = 0
    private var requestId = 0
    private var toolJob: Job? = null
    private var totalLlmMillis = 0L
    private val toolRecords = mutableListOf<ToolRecord>()
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
    private var sentModel = ""
    private var provider: String? = null
    private var firstTokenMillis: Long? = null
    private var usage: kotlinx.serialization.json.JsonObject? = null
    private var llmStats: LlmStats? = null

    private val settings get() = store.data.settings
    private val handsFree get() = settings.handsFree

    /** Om en tur er i gang: Kompis tenker eller snakker. */
    val isAnswering: Boolean get() = phase == Phase.Thinking || phase == Phase.Speaking

    fun start(history: List<Message>) {
        turns.clear()
        turns += history.filter { it.text.isNotBlank() || it.toolCalls.isNotEmpty() || it.role == Role.Tool }.takeLast(MaxHistory)
        phase = Phase.Opening
        host.onStatus("connecting")
        bridge.kompisConversationOpen(stt)
        bridge.speechStart(settings.ttsSampleRates[settings.ttsModel] ?: 0, speech)
        loadModelsIfNeeded()
    }

    fun stop() {
        if (phase == Phase.Closed) return
        if (isAnswering) reportInfo(interrupted = true)
        phase = Phase.Closed
        requestId++
        toolJob?.cancel()
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
                    turns += Message(Role.User, text, clock())
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
        sentModel = settings.llmModel
        provider = null
        firstTokenMillis = null
        usage = null
        llmStats = null
        chatSentAt = clock()
        round = 0
        toolRecords.clear()
        totalLlmMillis = 0
        requestRound()
    }

    private fun requestRound() {
        round++
        stream = SayStream()
        calls = ToolCallAssembler()
        usage = null
        chatDone = false
        val id = ++requestId
        val body = LlmRequest.body(
            modelId = settings.llmModel,
            providers = settings.llmProviders,
            routing = settings.llmRouting,
            model = ModelCache.llm.firstOrNull { it.id == settings.llmModel },
            prompt = VoicePrompt.build(settings.personality, now(), tools.all),
            // Trim only at user boundaries: tool results must retain their assistant calls.
            messages = requestHistory(),
            tools = tools.all,
        )
        // Every follow-up uses the native request path, including its pre-stream retry policy.
        bridge.openRouterChat(store.openRouterKey, body, object : ChatListener {
            override fun onEvent(dataJson: String, millis: Long) { if (id == requestId) chat.onEvent(dataJson, millis) }
            override fun onDone(millis: Long) { if (id == requestId) chat.onDone(millis) }
            override fun onError(message: String) { if (id == requestId) chat.onError(message) }
        })
    }

    private val chat = object : ChatListener {
        override fun onEvent(dataJson: String, millis: Long) {
            if (!isAnswering || chatDone) return
            val chunk = parseChatChunk(dataJson) ?: return
            if (chunk.error != null) {
                abort(chunk.error)
                return
            }
            chunk.provider?.let { provider = it }
            chunk.model?.let { sentModel = it }
            chunk.usage?.let { usage = it }
            // Tell første innhold/token, også JSON-argumenter før den talte teksten.
            if (firstTokenMillis == null && (chunk.content.isNotEmpty() || chunk.toolCalls.isNotEmpty())) {
                firstTokenMillis = totalLlmMillis + millis
            }
            // Modellen svarer med verktøyet say: først hva den tror du sa, så språket, og så det som skal sies.
            calls.add(chunk.toolCalls)
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
            val previous = llmStats
            totalLlmMillis += millis
            llmStats = LlmStats(
                modelName = sentModel,
                provider = provider,
                firstTokenMillis = firstTokenMillis,
                firstSentenceMillis = sentenceMillis,
                totalMillis = totalLlmMillis,
                promptTokens = usage?.int("prompt_tokens"),
                cachedTokens = usage?.get("prompt_tokens_details")?.jsonObjectOrNull()?.int("cached_tokens"),
                completionTokens = usage?.int("completion_tokens"),
                reasoningTokens = usage?.get("completion_tokens_details")?.jsonObjectOrNull()?.int("reasoning_tokens"),
                costUsd = (usage?.get("cost") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull(),
            )
            llmStats = llmStats!!.let { current -> current.copy(
                promptTokens = sum(previous?.promptTokens, current.promptTokens),
                cachedTokens = sum(previous?.cachedTokens, current.cachedTokens),
                completionTokens = sum(previous?.completionTokens, current.completionTokens),
                reasoningTokens = sum(previous?.reasoningTokens, current.reasoningTokens),
                costUsd = if (previous?.costUsd == null && current.costUsd == null) null else (previous?.costUsd ?: 0.0) + (current.costUsd ?: 0.0),
            ) }
            val completed = calls.all
            if (completed.any { it.name != "say" } || (completed.isNotEmpty() && completed.none { it.name == "say" })) {
                // Disallow mixed speech/actions: never execute after a confirmation has started playing.
                if (completed.any { it.name == "say" }) {
                    abort("Kompis blandet tale og handling i samme svar. Prøv igjen.")
                    return
                }
                executeCalls(completed)
                return
            }
            enqueue(buffer.toString())
            buffer.clear()
            llmStats = llmStats?.copy(firstSentenceMillis = sentenceMillis)
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

    private fun completeInterruptedCalls() {
        val assistantIndex = turns.indexOfLast { it.toolCalls.isNotEmpty() }
        if (assistantIndex < 0) return
        val calls = turns[assistantIndex].toolCalls
        val answered = turns.drop(assistantIndex + 1).mapNotNull { it.toolCallId }.toSet()
        calls.filterNot { it.id in answered }.forEach {
            turns += Message(Role.Tool, "Avbrutt. Handlingen kan ha blitt lagret; undersøk gjeldende tilstand.", clock(), toolCallId = it.id)
        }
    }

    private fun requestHistory(): List<Message> {
        val cutoff = (turns.size - MaxHistory).coerceAtLeast(0)
        val start = (cutoff downTo 0).firstOrNull { turns[it].role == Role.User } ?: 0
        return turns.drop(start)
    }

    private fun sum(a: Int?, b: Int?): Int? = if (a == null && b == null) null else (a ?: 0) + (b ?: 0)

    private fun executeCalls(completed: List<ToolCall>) {
        val id = requestId
        host.onStatus("working")
        toolJob = toolScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val normalized = completed.mapIndexed { index, call ->
                call.copy(id = call.id.ifBlank { "call_${round}_$index" })
            }
            turns += Message(Role.Kompis, "", clock(), toolCalls = normalized)
            for (call in normalized) {
                val result = try {
                    val tool = tools[call.name] ?: throw IllegalArgumentException("Ukjent verktøy: ${call.name}")
                    val args = json.parseToJsonElement(call.arguments) as? JsonObject
                        ?: throw IllegalArgumentException("Argumentene må være et JSON-objekt.")
                    tool.execute(args)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ToolResult(e.message ?: "Verktøyet feilet.", isError = true)
                }
                if (id != requestId || !isAnswering) return@launch
                toolRecords += ToolRecord(call.name, call.arguments, result.content, result.isError)
                result.card?.let(host::onToolCard)
                val content = buildJsonObject { put("content", result.content); put("isError", result.isError) }.toString()
                turns += Message(Role.Tool, content, clock(), toolCallId = call.id)
            }
            if (round >= MaxToolRounds) {
                enqueue("Jeg klarte ikke å fullføre innen fire forsøk. Prøv igjen.")
                bridge.speechFinish()
            } else { host.onStatus("thinking"); requestRound() }
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
        val index = turns.indexOfLast { it.role == Role.User }
        if (index < 0 || turns[index].text == understood) return
        host.onDebug("Forstod: $understood  (hørt som: ${turns[index].text})")
        turns[index] = turns[index].copy(text = understood)
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

    /** Forespørselen til stemmen. Stemmen og regien kommer fra innstillingene, aldri fra språkmodellen. */
    private fun speechBody(text: String): String = buildJsonObject {
        put("model", settings.ttsModel)
        put("input", text)
        voice()?.let { put("voice", it) }
        settings.ttsInstructions.trim().ifEmpty { null }?.let { put("instructions", it) }
        put("response_format", "pcm")
    }.toString()

    /** Stemmen som er valgt for modellen, ellers den første, de norske først. */
    private fun voice(): String? {
        val voices = ModelCache.tts.firstOrNull { it.id == settings.ttsModel }?.voices.orEmpty().sortedBy { !isNorwegianVoice(it) }
        return settings.ttsVoices[settings.ttsModel] ?: voices.firstOrNull()
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
            reportInfo(interrupted = false)
            turns += Message(Role.Kompis, sentences.joinToString(" "), clock())
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

    private fun reportInfo(interrupted: Boolean) {
        host.onMessageInfo(MessageInfo(
            engine = "OpenRouter",
            model = sentModel,
            stats = llmStats,
            timings = timings().copy(totalMillis = clock() - releasedAt),
            interrupted = interrupted,
            tools = toolRecords.toList(),
        ))
    }

    // Avbrudd og feil.

    /** Stopper svaret midt i. Det som er sagt så langt, blir med i samtalen. */
    private fun interrupt() {
        requestId++
        toolJob?.cancel()
        completeInterruptedCalls()
        reportInfo(interrupted = true)
        bridge.openRouterCancelChat()
        bridge.speechStop()
        keepWhatWasSaid()
        phase = Phase.Idle
        host.onInterrupted()
    }

    /** Noe gikk galt midt i svaret. Det som er sagt, blir stående, og samtalen fortsetter. */
    private fun abort(message: String) {
        requestId++
        toolJob?.cancel()
        completeInterruptedCalls()
        reportInfo(interrupted = true)
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
        if (said.isNotEmpty()) turns += Message(Role.Kompis, said, clock())
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
        const val MaxToolRounds = 4

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
