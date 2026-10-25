package no.jodal.salat.apps.kompis

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.jodal.salat.FakeBridge

class TtsTestTest {
    private val bridge = FakeBridge
    private lateinit var store: KompisStore
    private lateinit var test: TtsTest

    private val modelsJson = """{"data":[
        {"id":"mistralai/voxtral-mini-tts-3-0","name":"Mistral: Voxtral TTS 3","created":3,
         "supported_voices":["voice_3150","en_paul_happy"],"pricing":{"prompt":"0.000013","completion":"0"}},
        {"id":"microsoft/mai-voice-2.1","name":"Microsoft: MAI Voice 2.1","created":2,
         "supported_voices":["da-DK-Grant:MAI-Voice-2.1","nb-NO-Harper:MAI-Voice-2.1","nb-NO-Grant:MAI-Voice-2.1"]},
        {"id":"fish-audio/drama-3","name":"Fish Audio: Drama 3","created":1,"supported_voices":[],"pricing":{"prompt":"0.000002"}}
    ]}"""

    @BeforeTest
    fun setUp() {
        bridge.ttsCalls.clear()
        ModelCache.tts = emptyList()
        bridge.ttsSentWith = null
        bridge.openRouterGets.clear()
        bridge.openRouterResult = modelsJson to null
        store = KompisStore.get(bridge)
        store.saveOpenRouterKey("or-key")
        store.update { KompisData() }
        test = TtsTest(store, bridge)
        test.loadModels()
    }

    private fun sentBody() = Json.parseToJsonElement(bridge.ttsSentWith!!).jsonObject

    @Test
    fun speaksWithTheFirstVoiceAsRawAudio() {
        test.text = "Hei på deg"
        test.speak()
        assertEquals(listOf("speak tts-1 0 true"), bridge.ttsCalls)
        assertEquals(
            """{"model":"mistralai/voxtral-mini-tts-3-0","input":"Hei på deg","voice":"voice_3150","response_format":"pcm"}""",
            bridge.ttsSentWith,
        )
        val result = test.results.single()
        assertTrue(result.isLoading)
        assertTrue(test.isBusy)
        assertEquals("Voxtral TTS 3", result.modelName)
        assertEquals(0.000013 * 10, result.costUsd!!, 1e-12)
    }

    @Test
    fun putsNorwegianVoicesFirstAndRemembersTheVoicePerModel() {
        test.selectModel("microsoft/mai-voice-2.1")
        assertEquals(listOf("nb-NO-Harper:MAI-Voice-2.1", "nb-NO-Grant:MAI-Voice-2.1", "da-DK-Grant:MAI-Voice-2.1"), test.voices)
        assertEquals("nb-NO-Harper:MAI-Voice-2.1", test.voice)
        test.selectVoice("nb-NO-Grant:MAI-Voice-2.1")
        test.selectModel(DefaultTtsModel)
        assertEquals("voice_3150", test.voice)
        test.selectModel("microsoft/mai-voice-2.1")
        assertEquals("nb-NO-Grant:MAI-Voice-2.1", test.voice)
    }

    @Test
    fun sendsDirectionsAndNoVoiceForModelsWithoutAVoiceList() {
        test.selectModel("fish-audio/drama-3")
        test.setInstructions("  varm og rolig ")
        test.selectSampleRate(44_100)
        test.speak()
        assertNull(test.voice)
        assertNull(sentBody()["voice"])
        assertEquals("\"varm og rolig\"", sentBody()["instructions"].toString())
        assertEquals(listOf("speak tts-1 44100 true"), bridge.ttsCalls)
    }

    @Test
    fun tracksTimingAndPlayback() {
        test.speak()
        test.onPlaying("tts-1", true)
        test.onFirstAudio("tts-1", 310)
        test.onReceived("tts-1", 900, 3.2, 153_600, "pcm 24 kHz")
        assertEquals("tts-1", test.playingId)
        test.onPlaying("tts-1", false)
        assertNull(test.playingId)

        val result = test.results.single()
        assertFalse(result.isLoading)
        assertEquals(310L, result.firstAudioMillis)
        assertEquals(900L, result.totalMillis)
        assertEquals("pcm 24 kHz", result.format)
        assertEquals(310L, test.averageFirstAudio(DefaultTtsModel))

        test.replay("tts-1")
        assertEquals("replay tts-1", bridge.ttsCalls.last())
        assertEquals("tts-1", test.playingId)
    }

    @Test
    fun stoppingMarksWhatWasLoading() {
        test.speak()
        assertFalse(test.results.isEmpty())
        test.speak()
        assertEquals(1, test.results.size, "ett forsøk om gangen")
        test.stop()
        assertEquals("Stoppet.", test.results.single().error)
        assertFalse(test.isBusy)
    }

    @Test
    fun showsErrors() {
        test.speak()
        test.onError("tts-1", "OpenRouter svarte med feil 400: Unknown voice")
        assertEquals("OpenRouter svarte med feil 400: Unknown voice", test.results.single().error)
        assertNull(test.playingId)
    }

    @Test
    fun formatsPrices() {
        assertEquals("$13 per million tegn", formatPricePerMillion(0.000013, "tegn"))
        assertEquals("$0,62 per million tegn", formatPricePerMillion(0.00000062, "tegn"))
    }
}
