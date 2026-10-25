package no.jodal.salat.apps.kompis

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import no.jodal.salat.FakeBridge
import no.jodal.salat.apps.kompis.ui.LiveStatus

class KompisLiveTest {
    // KompisStore og KompisLive finnes bare én gang, så alle testene må bruke samme bro.
    private val bridge = FakeBridge
    private lateinit var store: KompisStore
    private lateinit var live: KompisLive

    @BeforeTest
    fun setUp() {
        bridge.startedWith = null
        bridge.rolledWith = null
        store = KompisStore.get(bridge)
        store.saveApiKey("test-key")
        store.update { KompisData() }
        live = KompisLive.get(store, bridge)
        live.end()
        bridge.startedWith = null
    }

    @Test
    fun savesTheConversationTurnByTurn() {
        live.start(continuing = null)
        assertEquals(LiveStatus.Connecting, live.state.status)
        assertEquals(emptyList(), store.data.conversations, "en tom samtale skal ikke lagres")

        live.onUserText("Hei! Jeg går ")
        live.onUserText("tur nå.")
        live.onKompisText("Så ")
        live.onKompisText("fint!")
        assertEquals("Så fint!", live.state.kompisDraft)
        live.onTurnComplete()

        val conversation = store.data.conversations.single()
        assertEquals("Hei", conversation.title)
        assertEquals(listOf(Role.User to "Hei! Jeg går tur nå.", Role.Kompis to "Så fint!"), conversation.messages.map { it.role to it.text })
        assertNull(live.state.userDraft)
        assertNull(live.state.kompisDraft)
    }

    @Test
    fun interruptingKeepsWhatKompisHadSaid() {
        live.start(continuing = null)
        live.onUserText("Fortell en lang historie")
        live.onKompisText("Det var en gang")
        live.onInterrupted()
        live.onUserText("Stopp")
        live.end()

        assertEquals(
            listOf("Fortell en lang historie", "Det var en gang", "Stopp"),
            store.data.conversations.single().messages.map { it.text },
        )
        assertEquals(LiveStatus.Ended, live.state.status)
    }

    @Test
    fun continuingSendsTheHistory() {
        val earlier = Conversation(
            id = "c1",
            title = "Tidligere",
            createdAtMillis = 0,
            updatedAtMillis = 0,
            messages = listOf(Message(Role.User, "Hei", 0), Message(Role.Kompis, "Hallo!", 0)),
        )
        store.update { it.copy(conversations = listOf(earlier)) }
        live.start(continuing = earlier)

        val config = Json.decodeFromString(LiveConfig.serializer(), bridge.startedWith!!)
        assertEquals(listOf("user" to "Hei", "model" to "Hallo!"), config.history.map { it.role to it.text })
        assertEquals(LiveModel, config.model)

        live.onUserText("Husker du meg?")
        live.onTurnComplete()
        val messages = store.conversation("c1")!!.messages
        assertEquals(3, messages.size)
        assertEquals("Tidligere", store.conversation("c1")!!.title, "tittelen skal ikke endres når samtalen fortsetter")
    }

    @Test
    fun withoutKeyNothingStarts() {
        store.saveApiKey("")
        live.start(continuing = null)
        assertEquals(LiveStatus.NeedsApiKey, live.state.status)
        assertNull(bridge.startedWith)
        assertTrue(!live.isActive)
    }

    @Test
    fun startsANewSessionWithTextMemoryEveryFewTurns() {
        live.start(continuing = null)
        repeat(KompisLive.TurnsPerSession - 1) { turn ->
            live.onUserText("Spørsmål $turn")
            live.onKompisText("Svar $turn")
            live.onTurnComplete()
        }
        assertNull(bridge.rolledWith, "ingen ny økt før nok turer")

        live.onUserText("Siste spørsmål")
        live.onKompisText("Siste svar")
        live.onTurnComplete()
        val history = Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(HistoryTurn.serializer()), bridge.rolledWith!!)
        assertEquals(KompisLive.TurnsPerSession * 2, history.size)
        assertEquals("user" to "Spørsmål 0", history.first().role to history.first().text)
        assertEquals("model" to "Siste svar", history.last().role to history.last().text)

        // Telleren starter på nytt.
        bridge.rolledWith = null
        live.onUserText("Enda en")
        live.onTurnComplete()
        assertNull(bridge.rolledWith)
    }

    @Test
    fun titles() {
        assertEquals("Kan du hjelpe meg med middag", KompisLive.titleFrom("Kan du hjelpe meg med middag? Jeg har egg."))
        assertEquals(46, KompisLive.titleFrom("a".repeat(80)).length)
    }

    @Test
    fun geminiLiveIsTheDefaultEngine() {
        assertEquals(KompisEngine.GeminiLive, store.engine)
        live.start(continuing = null)
        assertTrue(bridge.startedWith != null)
    }

    @Test
    fun theOpenRouterEngineNeedsItsOwnKey() {
        store.saveOpenRouterKey("")
        store.update { it.copy(settings = it.settings.copy(engine = KompisEngine.OpenRouter.id)) }
        assertEquals(KompisEngine.OpenRouter, store.engine)

        live.start(continuing = null)
        assertEquals(LiveStatus.NeedsApiKey, live.state.status)
        assertEquals("Legg inn en OpenRouter-nøkkel i innstillingene for å snakke med Kompis.", live.state.message)
        assertFalse(live.isActive)
        assertNull(bridge.startedWith, "Gemini skal ikke startes")
    }

    @Test
    fun theOpenRouterEngineDoesNotStartGeminiOrSaveAConversationYet() {
        store.saveOpenRouterKey("or-key")
        store.update { it.copy(settings = it.settings.copy(engine = KompisEngine.OpenRouter.id)) }

        live.start(continuing = null)
        assertEquals(LiveStatus.Failed, live.state.status)
        assertFalse(live.isActive)
        assertNull(bridge.startedWith)
        assertEquals(emptyList(), store.data.conversations)
    }

    @Test
    fun theGeminiKeyDoesNotCountForTheOpenRouterEngine() {
        assertTrue(store.hasKeyFor(KompisEngine.GeminiLive))
        store.saveOpenRouterKey("")
        assertFalse(store.hasKeyFor(KompisEngine.OpenRouter))
        assertEquals(KompisEngine.GeminiLive, KompisEngine.byId("noe-ukjent"))
    }
}
