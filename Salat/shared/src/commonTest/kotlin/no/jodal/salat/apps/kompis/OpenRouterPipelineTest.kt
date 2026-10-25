package no.jodal.salat.apps.kompis

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.jodal.salat.FakeBridge

/** Det samtalen får fra rørledningen, i den rekkefølgen det kom. */
private class RecordingHost : PipelineHost {
    val events = mutableListOf<String>()
    var timings: TurnTimings? = null

    override fun onStatus(status: String) { events += "status $status" }
    override fun onLevel(level: Float) { events += "level" }
    override fun onUserText(text: String) { events += "user $text" }
    override fun onKompisText(text: String) { events += "kompis $text" }
    override fun onTurnComplete() { events += "turn complete" }
    override fun onInterrupted() { events += "interrupted" }
    override fun onAudioRoute(name: String) { events += "route $name" }
    override fun onError(message: String) { events += "error $message" }
    override fun onDebug(text: String) { events += "debug $text" }
    override fun onNotice(message: String) { events += "notice $message" }
    override fun onTimings(timings: TurnTimings) {
        this.timings = timings
        events += "timings"
    }
}

class OpenRouterPipelineTest {
    private val bridge = FakeBridge
    private lateinit var store: KompisStore
    private lateinit var host: RecordingHost
    private lateinit var pipeline: OpenRouterPipeline
    private var clockMillis = 1_000_000L

    @BeforeTest
    fun setUp() {
        bridge.pipelineCalls.clear()
        bridge.speechBodies.clear()
        bridge.chatSentWith = null
        bridge.sttStopConfig = null
        bridge.chatCancelled = false
        bridge.openRouterResult = null
        store = KompisStore.get(bridge)
        store.saveOpenRouterKey("or-key")
        store.update { KompisData() }
        ModelCache.llm = emptyList()
        ModelCache.tts = listOf(
            OpenRouterModel("mistralai/voxtral-mini-tts-3-0", "Mistral: Voxtral TTS 3", 1, voices = listOf("voice_3150", "nb-NO-Harper")),
        )
        host = RecordingHost()
        clockMillis = 1_000_000L
        pipeline = OpenRouterPipeline(store, bridge, host, now = { LocalDateTime(2026, 10, 25, 14, 32) }, clock = { clockMillis })
    }

    private val stt get() = bridge.sttListener!!
    private val chat get() = bridge.chatListener!!
    private val speech get() = bridge.speechListener!!

    private fun openAndGoToListening(history: List<Message> = emptyList()) {
        pipeline.start(history)
        stt.onMicrophone("iPhone-mikrofon")
        host.events.clear()
        bridge.pipelineCalls.clear()
    }

    /** Holder inne, snakker, slipper, og får teksten tilbake. */
    private fun say(text: String) {
        pipeline.talkStart()
        pipeline.talkStop()
        stt.onTranscribed("""{"text":"$text","usage":{"cost":0.0001}}""", 2.0, 64_000, 210)
    }

    private fun lastRequest() = Json.parseToJsonElement(bridge.chatSentWith!!).jsonObject

    private fun chunk(text: String) = chat.onEvent("""{"choices":[{"delta":{"content":${JsonPrimitive(text)}}}]}""", 300)

    @Test
    fun opensTheMicrophoneAndTheVoiceThenListens() {
        pipeline.start(emptyList())
        assertEquals(listOf("mic opened", "speech start 0"), bridge.pipelineCalls)
        assertEquals(listOf("status connecting"), host.events)
        stt.onMicrophone("AirPods Pro")
        assertEquals(listOf("status connecting", "route AirPods Pro", "status listening"), host.events)
    }

    @Test
    fun aWholeTurnFromTheButtonToTheVoice() {
        openAndGoToListening()

        pipeline.talkStart()
        assertEquals(listOf("status recording"), host.events)
        assertEquals(listOf("record"), bridge.pipelineCalls)
        stt.onLevel(0.4f)
        clockMillis += 2_000
        pipeline.talkStop()
        assertEquals(listOf("record", "send"), bridge.pipelineCalls)
        val config = Json.decodeFromString(SttConfig.serializer(), bridge.sttStopConfig!!)
        assertEquals(SttConfig("or-key", "mistralai/voxtral-mini-transcribe-3-0", "no"), config)

        stt.onTranscribed("""{"text":"Hva er en hare?","usage":{"cost":0.0001}}""", 2.0, 64_000, 210)
        assertTrue("user Hva er en hare?" in host.events)
        val request = lastRequest()
        assertEquals("deepseek/deepseek-v4-flash-0731", request["model"]!!.jsonPrimitive.content)
        val messages = request["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user"), messages.map { it["role"]!!.jsonPrimitive.content })
        assertEquals("Hva er en hare?", messages[1]["content"]!!.jsonPrimitive.content)

        // Den første setningen leses opp før svaret er ferdig skrevet.
        clockMillis += 400
        chunk("En hare er et dyr. ")
        assertEquals(listOf("speak s0"), bridge.pipelineCalls.drop(2))
        chunk("Den er")
        chunk(" rask. Og")
        assertEquals(listOf("speak s0", "speak s1"), bridge.pipelineCalls.drop(2))
        chunk(" stor!")
        chat.onDone(900)
        assertEquals(listOf("speak s0", "speak s1", "speak s2", "finish"), bridge.pipelineCalls.drop(2))

        val body = Json.parseToJsonElement(bridge.speechBodies["s1"]!!).jsonObject
        assertEquals("Den er rask.", body["input"]!!.jsonPrimitive.content)
        assertEquals("mistralai/voxtral-mini-tts-3-0", body["model"]!!.jsonPrimitive.content)
        assertEquals("nb-NO-Harper", body["voice"]!!.jsonPrimitive.content, "norsk stemme først")
        assertEquals("pcm", body["response_format"]!!.jsonPrimitive.content)

        // Setningene kommer i teksten når de begynner å spilles.
        clockMillis += 300
        speech.onFirstAudio("s0", 240)
        speech.onItemStarted("s0")
        speech.onItemFinished("s0")
        speech.onItemStarted("s1")
        speech.onItemFinished("s1")
        speech.onItemStarted("s2")
        speech.onItemFinished("s2")
        speech.onFinished()

        val spoken = host.events.filter { it.startsWith("kompis") }
        assertEquals(listOf("kompis En hare er et dyr.", "kompis  Den er rask.", "kompis  Og stor!"), spoken)
        assertEquals("status speaking", host.events.first { it.startsWith("status speaking") })
        assertEquals(listOf("turn complete", "status listening", "timings"), host.events.takeLast(3))

        val timings = host.timings!!
        assertEquals(210L, timings.sttMillis)
        assertEquals(400L, timings.sentenceMillis)
        assertEquals(240L, timings.voiceMillis)
        assertEquals(700L, timings.heardAfterMillis, "400 til setningen og 300 til lyden, fra han slapp knappen")
        assertNotNull(timings.totalMillis)
    }

    @Test
    fun theNextTurnRemembersWhatWasSaid() {
        openAndGoToListening(listOf(Message(Role.User, "Hei", 1), Message(Role.Kompis, "Hei på deg!", 2)))
        say("Hvordan går det?")
        chunk("Bare bra. ")
        chunk("Og med deg?")
        chat.onDone(500)
        speech.onItemStarted("s0")
        speech.onItemStarted("s1")
        speech.onFinished()

        say("Fint")
        val messages = lastRequest()["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(
            listOf("system", "user", "assistant", "user", "assistant", "user"),
            messages.map { it["role"]!!.jsonPrimitive.content },
        )
        assertEquals(
            listOf("Hei", "Hei på deg!", "Hvordan går det?", "Bare bra. Og med deg?", "Fint"),
            messages.drop(1).map { it["content"]!!.jsonPrimitive.content },
        )
        assertTrue(messages[0]["content"]!!.jsonPrimitive.content.endsWith("Det er søndag 25. oktober 2026, og klokka er 14:32."))
    }

    @Test
    fun interruptingKeepsOnlyWhatWasSpokenAndStartsRecording() {
        openAndGoToListening()
        say("Fortell en historie")
        chunk("Det var en gang. ")
        chunk("En hare løp. ")
        chunk("Så")
        speech.onItemStarted("s0")
        speech.onItemFinished("s0")
        speech.onItemStarted("s1")
        bridge.pipelineCalls.clear()
        host.events.clear()

        pipeline.talkStart()
        assertEquals(listOf("speech stop", "record"), bridge.pipelineCalls)
        assertEquals(listOf("interrupted", "status recording"), host.events)
        assertTrue(bridge.chatCancelled)

        // Lyd som kommer etter avbruddet, blir ikke med.
        host.events.clear()
        speech.onFinished()
        assertEquals(emptyList(), host.events)

        // Det som ble sagt før avbrudd, er med i minnet. Resten av svaret er ikke det.
        pipeline.talkStop()
        stt.onTranscribed("""{"text":"Stopp"}""", 1.0, 32_000, 200)
        val roles = lastRequest()["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals("Det var en gang. En hare løp.", roles[roles.size - 2]["content"]!!.jsonPrimitive.content)
        assertEquals("assistant", roles[roles.size - 2]["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun anEmptyTranscriptDoesNotCallTheLanguageModel() {
        openAndGoToListening()
        pipeline.talkStart()
        pipeline.talkStop()
        bridge.chatSentWith = null
        stt.onTranscribed("""{"text":"  "}""", 1.0, 32_000, 200)
        assertNull(bridge.chatSentWith)
        assertEquals(listOf("status recording", "status thinking", "status listening", "notice Hørte ikke noe. Prøv igjen."), host.events)
    }

    @Test
    fun theButtonDoesNothingWhileTheRecordingIsTranscribed() {
        openAndGoToListening()
        pipeline.talkStart()
        pipeline.talkStop()
        bridge.pipelineCalls.clear()
        pipeline.talkStart()
        pipeline.talkStop()
        assertEquals(emptyList(), bridge.pipelineCalls)
    }

    @Test
    fun aFailureInTheMiddleKeepsTheConversationGoing() {
        openAndGoToListening()
        say("Hei")
        chunk("Hei! ")
        chat.onEvent("""{"error":{"code":502,"message":"Wafer er nede"},"choices":[]}""", 400)
        assertEquals(listOf("interrupted", "status listening", "notice Wafer er nede"), host.events.takeLast(3))

        host.events.clear()
        say("Prøv igjen")
        speech.onError("Stemmen er nede")
        assertEquals(listOf("interrupted", "status listening", "notice Stemmen feilet: Stemmen er nede"), host.events.takeLast(3))
    }

    @Test
    fun noAnswerBringsYouBackToListening() {
        openAndGoToListening()
        say("Hei")
        chat.onDone(300)
        assertEquals(listOf("status listening", "notice Kompis ga ikke noe svar."), host.events.takeLast(2))
        assertTrue("finish" !in bridge.pipelineCalls)
    }

    @Test
    fun longSentencesWithoutAPeriodAreSplitAtACommaAndCleaned() {
        openAndGoToListening()
        say("Fortell")
        val long = "Dette er en veldig lang setning som bare fortsetter og fortsetter uten noe punktum, og den " +
            "blir enda lengre fordi den har mange ledd som henger sammen på rekke og rad, så stemmen må begynne før den er ferdig"
        chunk("**Hei** 😀 ")
        chunk(long)
        val first = Json.parseToJsonElement(bridge.speechBodies["s0"]!!).jsonObject["input"]!!.jsonPrimitive.content
        assertTrue(first.endsWith("rekke og rad,"), first)
        assertEquals(1, bridge.speechBodies.size)
        chat.onDone(900)
        assertEquals("så stemmen må begynne før den er ferdig", Json.parseToJsonElement(bridge.speechBodies["s1"]!!).jsonObject["input"]!!.jsonPrimitive.content)
    }

    @Test
    fun usesTheVoiceFromTheSettingsAndTheSampleRate() {
        store.update { it.copy(settings = it.settings.copy(ttsVoices = mapOf(DefaultTtsModel to "voice_3150"), ttsSampleRates = mapOf(DefaultTtsModel to 24_000), ttsInstructions = "rolig")) }
        pipeline.start(emptyList())
        assertEquals("speech start 24000", bridge.pipelineCalls.last())
        stt.onMicrophone("iPhone")
        say("Hei")
        chunk("Hei på deg. ")
        val body = Json.parseToJsonElement(bridge.speechBodies["s0"]!!).jsonObject
        assertEquals("voice_3150", body["voice"]!!.jsonPrimitive.content)
        assertEquals("rolig", body["instructions"]!!.jsonPrimitive.content)
    }

    @Test
    fun stoppingClosesEverything() {
        openAndGoToListening()
        pipeline.stop()
        assertEquals(listOf("speech stop", "mic closed"), bridge.pipelineCalls)
        assertTrue(bridge.chatCancelled)
        bridge.pipelineCalls.clear()
        pipeline.talkStart()
        assertEquals(emptyList(), bridge.pipelineCalls)
    }

    @Test
    fun aBrokenMicrophoneEndsTheConversation() {
        pipeline.start(emptyList())
        stt.onMicrophoneError("Kompis har ikke tilgang til mikrofonen.")
        assertEquals("error Kompis har ikke tilgang til mikrofonen.", host.events.last())
        assertTrue("mic closed" in bridge.pipelineCalls)
    }

    @Test
    fun formatsTheTimings() {
        assertEquals(
            "Tale til tekst 210 ms  ·  Språkmodell 400 ms  ·  Stemme 240 ms  ·  Hørt etter 700 ms  ·  ferdig 3,2 s",
            formatTimings(TurnTimings(210, 400, 240, 700, 3200)),
        )
        assertEquals("Tale til tekst 210 ms", formatTimings(TurnTimings(210, null, null, null)))
    }
}
