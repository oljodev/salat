package no.jodal.salat.apps.kompis

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.jodal.salat.LiveListener
import no.jodal.salat.NativeBridge
import no.jodal.salat.apps.kompis.ui.LiveStatus
import no.jodal.salat.apps.kompis.ui.LiveUiState

/** Modellen Kompis bruker. */
const val LiveModel = "gemini-3.8-live"

/** Det Swift trenger for å starte en samtale. Sendes som JSON. */
@Serializable
data class LiveConfig(
    val apiKey: String,
    val model: String,
    val voice: String,
    val systemInstruction: String,
    /** Tidligere meldinger, når en samtale fortsettes. */
    val history: List<HistoryTurn>,
)

@Serializable
data class HistoryTurn(
    /** `user` eller `model`, som i Gemini. */
    val role: String,
    val text: String,
)

/**
 * Den pågående samtalen med Gemini Live. Det finnes bare én om gangen, og den fortsetter selv om
 * man går ut av samtaleskjermen.
 */
class KompisLive private constructor(private val store: KompisStore, private val bridge: NativeBridge) : PipelineHost {
    var state by mutableStateOf(LiveUiState())
        private set

    /** Samtalen som pågår, eller `null`. */
    var conversationId by mutableStateOf<String?>(null)
        private set

    /** Når samtalen startet, for å vise hvor lenge den har vart. */
    var startedAtMillis by mutableStateOf(0L)
        private set

    val isActive: Boolean get() = conversationId != null

    /** Hvor mange turer siden forrige gang Kompis startet en ny økt med tekstminne. */
    private var turnsSinceRoll = 0

    /** Samtalen gjennom OpenRouter som pågår, eller `null` når den er med Gemini Live. */
    private var pipeline: OpenRouterPipeline? = null

    /** Starter en ny samtale, eller fortsetter [continuing]. */
    fun start(continuing: Conversation?) {
        if (isActive) return
        val now = store.nowMillis()
        val conversation = continuing ?: Conversation(
            id = newId(),
            title = "Ny samtale",
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        val engine = store.engine
        val hasKey = store.hasKeyFor(engine)
        pipeline = null
        conversationId = conversation.id
        startedAtMillis = now
        turnsSinceRoll = 0
        state = LiveUiState(
            title = conversation.title,
            status = if (hasKey) LiveStatus.Connecting else LiveStatus.NeedsApiKey,
            messages = conversation.messages,
            message = if (hasKey) null else "Legg inn en ${engine.keyName}-nøkkel i innstillingene for å snakke med Kompis.",
        )
        if (!hasKey) {
            conversationId = null
            return
        }
        if (engine == KompisEngine.OpenRouter) {
            state = state.copy(pushToTalk = true, handsFree = store.data.settings.handsFree)
            // En ny samtale lagres først når noe er sagt, så tomme samtaler ikke blir liggende.
            if (continuing != null) save(conversation)
            val started = OpenRouterPipeline(store, bridge, this)
            pipeline = started
            started.start(conversation.messages)
            return
        }

        val config = LiveConfig(
            apiKey = store.apiKey,
            model = LiveModel,
            voice = store.data.settings.voice,
            systemInstruction = systemInstruction(store.data.settings.personality),
            history = history(conversation.messages),
        )
        // En ny samtale lagres først når noe er sagt, så tomme samtaler ikke blir liggende.
        if (continuing != null) save(conversation)
        bridge.startLiveSession(json.encodeToString(LiveConfig.serializer(), config), this)
    }

    fun end() {
        if (!isActive) return
        val wasPipeline = pipeline != null
        stopPipeline()
        if (!wasPipeline) bridge.stopLiveSession()
        finishTurn()
        saveDuration()
        conversationId = null
        state = state.copy(status = LiveStatus.Ended, level = 0f)
    }

    /** Knappen for å snakke trykkes, når samtalen går gjennom OpenRouter. */
    fun talkStart() {
        pipeline?.talkStart()
    }

    /** Knappen slippes, eller trykkes én gang til. */
    fun talkStop() {
        pipeline?.talkStop()
    }

    /** Et trykk på knappen i håndfri modus. */
    fun talkTap() {
        pipeline?.tap()
    }

    /** Bytter mellom håndfri og knapp. Valget huskes til neste samtale. */
    fun toggleHandsFree() {
        val pipeline = pipeline ?: return
        val on = !store.data.settings.handsFree
        store.update { it.copy(settings = it.settings.copy(handsFree = on)) }
        state = state.copy(handsFree = on)
        pipeline.handsFreeChanged()
    }

    private fun stopPipeline() {
        pipeline?.stop()
        pipeline = null
    }

    fun toggleMute() {
        if (pipeline != null) return
        val muted = !state.muted
        bridge.setLiveMuted(muted)
        state = state.copy(muted = muted, status = if (muted) LiveStatus.Muted else LiveStatus.Listening)
    }

    // LiveListener, kalles fra Swift.

    override fun onStatus(status: String) {
        val newStatus = when (status) {
            "connecting" -> LiveStatus.Connecting
            "listening" -> if (state.muted) LiveStatus.Muted else LiveStatus.Listening
            "recording" -> LiveStatus.Recording
            "thinking" -> LiveStatus.Thinking
            "speaking" -> LiveStatus.Speaking
            "reconnecting" -> LiveStatus.Reconnecting
            "ended" -> LiveStatus.Ended
            else -> return
        }
        state = state.copy(status = newStatus, message = null)
    }

    override fun onLevel(level: Float) {
        state = state.copy(level = level)
    }

    override fun onUserText(text: String) {
        // Brukeren begynner å snakke igjen: det Kompis sa før er ferdig.
        if (state.kompisDraft != null) finishTurn()
        state = state.copy(userDraft = (state.userDraft.orEmpty() + text).trimStart())
    }

    override fun onKompisText(text: String) {
        // Kompis begynner å svare: det brukeren sa er ferdig.
        state.userDraft?.let { addMessage(Role.User, it) }
        state = state.copy(userDraft = null, kompisDraft = (state.kompisDraft.orEmpty() + text).trimStart())
    }

    override fun onTurnComplete() {
        finishTurn()
        // Hver 4. tur starter Kompis en ny økt med samtalen som tekst, så lyden ikke faktureres på nytt.
        if (pipeline != null) return
        turnsSinceRoll++
        if (turnsSinceRoll >= TurnsPerSession && isActive) {
            turnsSinceRoll = 0
            bridge.rollLiveSession(json.encodeToString(historySerializer, history(state.messages)))
        }
    }

    override fun onInterrupted() = finishTurn()

    override fun onAudioRoute(name: String) {
        state = state.copy(audioRoute = name)
    }

    override fun onDebug(text: String) {
        state = state.copy(debug = text)
    }

    override fun onNotice(message: String) {
        state = state.copy(message = message)
    }

    override fun onTimings(timings: TurnTimings) {
        state = state.copy(timings = timings)
    }

    override fun onError(message: String) {
        stopPipeline()
        finishTurn()
        saveDuration()
        conversationId = null
        state = state.copy(status = LiveStatus.Failed, message = message, level = 0f)
    }

    // Lagring

    private fun finishTurn() {
        state.userDraft?.let { addMessage(Role.User, it) }
        state.kompisDraft?.let { addMessage(Role.Kompis, it) }
        state = state.copy(userDraft = null, kompisDraft = null)
    }

    private fun addMessage(role: Role, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return
        }
        val message = Message(role, trimmed, store.nowMillis())
        state = state.copy(messages = state.messages + message)
        val id = conversationId ?: return
        val existing = store.conversation(id)
        val conversation = (existing ?: Conversation(id, state.title, startedAtMillis, startedAtMillis)).let {
            val title = if (it.messages.none { m -> m.role == Role.User } && role == Role.User) titleFrom(trimmed) else it.title
            it.copy(title = title, messages = it.messages + message, updatedAtMillis = message.atMillis)
        }
        state = state.copy(title = conversation.title)
        save(conversation)
    }

    private fun saveDuration() {
        val id = conversationId ?: return
        val conversation = store.conversation(id) ?: return
        val seconds = ((store.nowMillis() - startedAtMillis) / 1000).toInt()
        save(conversation.copy(durationSeconds = conversation.durationSeconds + seconds))
    }

    private fun save(conversation: Conversation) {
        store.update { data ->
            data.copy(conversations = data.conversations.filter { it.id != conversation.id } + conversation)
        }
    }

    companion object {
        /** Hvor mange tidligere meldinger som sendes med som tekst. Tekst er billig, så det kan være mange. */
        private const val MaxHistory = 100

        /**
         * Hvor mange turer Kompis tar før den starter en ny økt med tekstminne. Oftere er billigere,
         * men hvert bytte tar et øyeblikk. Hver 4. tur koster omtrent 16 kr i timen, mot 157 kr uten.
         */
        const val TurnsPerSession = 4

        private val historySerializer = kotlinx.serialization.builtins.ListSerializer(HistoryTurn.serializer())

        private fun history(messages: List<Message>): List<HistoryTurn> = messages.takeLast(MaxHistory).map {
            HistoryTurn(role = if (it.role == Role.User) "user" else "model", text = it.text)
        }
        private val json = Json { encodeDefaults = true }
        private var instance: KompisLive? = null

        fun get(store: KompisStore, bridge: NativeBridge): KompisLive =
            instance ?: KompisLive(store, bridge).also { instance = it }

        private fun newId(): String = (1..12).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")

        /** Tittelen på en samtale: starten på det første brukeren sa. */
        fun titleFrom(text: String): String {
            val firstSentence = text.split('.', '?', '!').first().trim()
            return if (firstSentence.length <= 48) firstSentence else firstSentence.take(45).trimEnd() + "…"
        }

        /** Personligheten fra innstillingene, og reglene som alltid gjelder. */
        fun systemInstruction(personality: String): String {
            val who = personality.trim().takeUnless { it == OldDefaultPersonality } ?: DefaultPersonality
            return who + "\n\n" + Rules
        }

        /** Korte regler. Språket står først, fordi Gemini ellers kan bytte språk når den hører dårlig. */
        private val Rules = """
            Regler:
            - Snakk alltid norsk bokmål, også når brukeren bruker engelske ord eller du hører dårlig. Bytt bare språk hvis brukeren ber om det.
            - Dette er en muntlig samtale. Svar kort og naturlig, vanligvis én til tre setninger, som til en venn.
            - Ingen lister, overskrifter, emojier eller lenker. Alt du sier blir lest opp.
            - Hørte du ikke hva brukeren sa, spør kort om det igjen i stedet for å gjette.
            - Still et oppfølgingsspørsmål av og til, ikke etter hvert svar.
        """.trimIndent()
    }
}
