package no.jodal.salat.apps.kompis

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import no.jodal.salat.FakeBridge

class SttTestTest {
    private val bridge = FakeBridge
    private lateinit var store: KompisStore
    private lateinit var test: SttTest

    @BeforeTest
    fun setUp() {
        bridge.sttSentWith = null
        bridge.sttResendConfig = null
        bridge.openRouterGets.clear()
        bridge.openRouterResult = null
        store = KompisStore.get(bridge)
        store.saveOpenRouterKey("or-key")
        store.update { KompisData() }
        test = SttTest(store, bridge)
    }

    @Test
    fun sendsTheChosenModelAndLanguage() {
        test.selectModel("openai/whisper-large-v3-turbo")
        test.selectLanguage(SttLanguage.Auto)
        test.startRecording()
        assertTrue(test.isRecording)
        test.stopRecording()
        assertTrue(test.isWaiting)

        val config = Json.decodeFromString(SttConfig.serializer(), bridge.sttSentWith!!)
        assertEquals(SttConfig("or-key", "openai/whisper-large-v3-turbo", null), config)
    }

    @Test
    fun defaultsToVoxtralMiniInNorwegian() {
        test.startRecording()
        test.stopRecording()
        val config = Json.decodeFromString(SttConfig.serializer(), bridge.sttSentWith!!)
        assertEquals("mistralai/voxtral-mini-transcribe-3-0", config.model)
        assertEquals("no", config.language)
    }

    @Test
    fun keepsResultsNewestFirstWithTheModelTheyWereSentWith() {
        test.startRecording()
        test.stopRecording()
        // Modellen endres mens svaret er på vei, men resultatet hører til modellen det ble sendt med.
        test.selectModel("mistralai/voxtral-small-transcribe-3-0")
        test.onTranscribed("""{"text":" Hei på deg ","usage":{"cost":0.00014,"seconds":2.4},"extra":1}""", 2.4, 76_844, 640)

        test.startRecording()
        test.stopRecording()
        test.onTranscribed("""{"text":"Hola"}""", 1.0, 32_044, 900)

        assertFalse(test.isWaiting)
        assertEquals(listOf("Hola", "Hei på deg"), test.results.map { it.text })
        assertEquals(DefaultSttModel, test.results[1].modelId)
        assertEquals(0.00014, test.results[1].costUsd)
        assertNull(test.results[0].costUsd)
        assertEquals(640L, test.averageMillis(DefaultSttModel))
        assertEquals(900L, test.averageMillis("mistralai/voxtral-small-transcribe-3-0"))
        assertNull(test.averageMillis("openai/whisper-1"))
    }

    @Test
    fun aShortTapKeepsRecordingUntilTheNextTap() {
        test.startRecording()
        test.switchToTapMode()
        assertTrue(test.isRecording)
        assertTrue(test.isTapMode)
        test.stopRecording()
        assertFalse(test.isTapMode)
        assertTrue(test.isWaiting)
    }

    @Test
    fun fetchesModelsNewestFirst() {
        bridge.openRouterResult = """{"data":[
            {"id":"openai/whisper-1","name":"OpenAI: Whisper","created":1700000000,"pricing":{}},
            {"id":"mistralai/voxtral-mini-transcribe-3-0","name":"Mistral: Voxtral Mini Transcribe 3","created":1792540800},
            {"id":"elevenlabs/scribe-v2","name":"ElevenLabs: Scribe v2","created":1791331200}
        ]}""" to null
        test.loadModels()

        assertEquals(listOf(OpenRouter.SttModelsPath), bridge.openRouterGets)
        assertEquals(
            listOf("mistralai/voxtral-mini-transcribe-3-0", "elevenlabs/scribe-v2", "openai/whisper-1"),
            test.models.map { it.id },
        )
        assertEquals("Voxtral Mini Transcribe 3", test.modelName(DefaultSttModel))
        assertEquals("Mistral", test.models.first().maker)
        assertEquals("ukjent-modell", test.modelName("noen/ukjent-modell"))
    }

    @Test
    fun showsWhyModelsCouldNotBeFetched() {
        bridge.openRouterResult = null to "OpenRouter godtok ikke nøkkelen."
        test.loadModels()
        assertFalse(test.isLoadingModels)
        assertEquals("OpenRouter godtok ikke nøkkelen.", test.modelsError)
    }

    @Test
    fun needsAKey() {
        store.saveOpenRouterKey(" ")
        test.startRecording()
        assertFalse(test.isRecording)
        assertEquals("Legg inn en OpenRouter-nøkkel i innstillingene først.", test.message)
        test.loadModels()
        assertEquals(emptyList(), bridge.openRouterGets)
    }

    @Test
    fun showsErrors() {
        test.startRecording()
        test.stopRecording()
        test.onError("Nøkkelen virker ikke.")
        assertFalse(test.isWaiting)
        assertEquals("Nøkkelen virker ikke.", test.message)

        test.startRecording()
        assertNull(test.message, "et nytt forsøk fjerner feilen")
    }

    @Test
    fun microphoneErrorsStayUntilTheMicrophoneWorks() {
        test.onMicrophoneError("Mikrofonen gir ingen lyd.")
        test.startRecording()
        assertEquals("Mikrofonen gir ingen lyd.", test.microphoneError)
        test.onMicrophone("iPhone-mikrofon")
        assertNull(test.microphoneError)
        assertEquals("iPhone-mikrofon", test.microphone)
    }

    @Test
    fun formatsNumbers() {
        assertEquals("2,4 s", formatSeconds(2.36))
        assertEquals("0,0 s", formatSeconds(0.01))
        assertEquals("77 kB", formatKilobytes(76_844))
        assertEquals("$0,00014", formatUsd(0.00014))
    }

    private fun decode(config: String?) = Json.decodeFromString(SttConfig.serializer(), config!!)

    @Test
    fun limitedAutoSendsAgainAsNorwegianWhenAnotherLanguageIsHeard() {
        var now = 5_000L
        test = SttTest(store, bridge, clock = { now })
        test.selectLanguage(SttLanguage.Limited)
        test.startRecording()
        test.stopRecording()
        assertEquals(SttConfig("or-key", DefaultSttModel, null, verbose = true), decode(bridge.sttSentWith))

        now += 700
        test.onTranscribed("""{"text":"Tusen tack","language":"sv"}""", 2.0, 64_000, 500)
        assertTrue(test.isWaiting, "venter på svaret fra den nye sendingen")
        assertEquals(emptyList(), test.results)
        assertEquals(SttConfig("or-key", DefaultSttModel, "no"), decode(bridge.sttResendConfig))

        now += 300
        test.onTranscribed("""{"text":"Tusen takk","language":"no"}""", 2.0, 64_000, 280)
        assertFalse(test.isWaiting)
        val result = test.results.single()
        assertEquals("Tusen takk", result.text)
        assertEquals(1000L, result.millis, "begge sendingene regnes med")
        assertEquals("Oppdaget svensk, prøvde igjen som norsk", result.note)
    }

    @Test
    fun limitedAutoAcceptsTheLanguagesYouCanSpeak() {
        test.selectLanguage(SttLanguage.Limited)
        for ((heard, name) in listOf("english" to "engelsk", "es" to "spansk", "Norwegian" to "norsk", "nb" to "norsk")) {
            bridge.sttResendConfig = null
            test.startRecording()
            test.stopRecording()
            test.onTranscribed("""{"text":"Hello","language":"$heard"}""", 1.0, 32_000, 300)
            assertNull(bridge.sttResendConfig, heard)
            assertEquals("Oppdaget $name", test.results.first().note)
        }
    }

    @Test
    fun limitedAutoWithoutALanguageInTheAnswerIsAccepted() {
        test.selectLanguage(SttLanguage.Limited)
        test.startRecording()
        test.stopRecording()
        test.onTranscribed("""{"text":"Hei"}""", 1.0, 32_000, 300)
        assertNull(bridge.sttResendConfig)
        assertEquals("Modellen sa ikke hvilket språk den hørte", test.results.single().note)
    }

    @Test
    fun aChosenLanguageIsNeverChecked() {
        test.selectLanguage(SttLanguage.Norwegian)
        test.startRecording()
        test.stopRecording()
        test.onTranscribed("""{"text":"Hej","language":"sv"}""", 1.0, 32_000, 300)
        assertNull(bridge.sttResendConfig)
        assertNull(test.results.single().note)
    }

    @Test
    fun normalizesLanguageNames() {
        assertEquals("no", OpenRouter.normalizeLanguage("Norwegian"))
        assertEquals("no", OpenRouter.normalizeLanguage(" nb "))
        assertEquals("en", OpenRouter.normalizeLanguage("English"))
        assertEquals("es", OpenRouter.normalizeLanguage("spanish"))
        assertEquals("sv", OpenRouter.normalizeLanguage("SV"))
        assertEquals("svensk", OpenRouter.languageName("sv"))
        assertEquals("norsk", OpenRouter.languageName("norwegian"))
        assertEquals("sv", OpenRouter.parseDetectedLanguage("""{"text":"x","language":"sv"}"""))
        assertNull(OpenRouter.parseDetectedLanguage("""{"text":"x","language":null}"""))
        assertNull(OpenRouter.parseDetectedLanguage("""{"text":"x"}"""))
    }
}
