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
    var info: MessageInfo? = null

    override fun onMessageInfo(info: MessageInfo) { this.info = info }

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
    @Test
    fun messageMetricsUseTokenTimeAndExcludeSpeechPlayback() {
        openAndGoToListening()
        say("Hei")
        chat.onEvent("""{"model":"actual/model","provider":"Test","choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"say","arguments":"{"}}]}}]}""", 100)
        val arguments = JsonPrimitive("\"text\":\"Hei på deg. \"}")
        chat.onEvent("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"say","arguments":$arguments}}]}}]}""", 300)
        chat.onEvent("""{"choices":[],"usage":{"prompt_tokens":200,"completion_tokens":101,"completion_tokens_details":{"reasoning_tokens":20},"cost":0.002}}""", 1100)
        chat.onDone(1100)
        clockMillis += 15_000
        speech.onItemStarted("s0")
        speech.onFinished()
        val info = assertNotNull(host.info)
        assertEquals("actual/model", info.model)
        assertEquals("Test", info.stats?.provider)
        assertEquals(80, info.stats?.tokensPerSecond)
        assertEquals(100L, info.stats?.firstTokenMillis)
        assertEquals(1100L, info.stats?.totalMillis)
        assertEquals(200, info.stats?.promptTokens)
        assertEquals(0.002, info.stats?.costUsd)
        assertEquals(false, info.interrupted)
        assertTrue(info.timings!!.totalMillis!! >= 15_000)

        // Neste svar skal ikke arve forbruket eller TPS fra det forrige.
        say("Igjen")
        chunk("Hei igjen. ")
        speech.onItemStarted("s0")
        pipeline.talkStart()
        val partial = assertNotNull(host.info)
        assertTrue(partial.interrupted)
        assertNull(partial.stats)
    }

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
        bridge.sttResendConfig = null
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
        assertEquals(listOf("speak s0"), bridge.pipelineCalls.drop(2))
        chunk(" stor!")
        chat.onDone(900)
        assertEquals(listOf("speak s0", "speak s1", "finish"), bridge.pipelineCalls.drop(2))

        val body = Json.parseToJsonElement(bridge.speechBodies["s1"]!!).jsonObject
        assertEquals("Den er rask. Og stor!", body["input"]!!.jsonPrimitive.content)
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
        speech.onFinished()

        val spoken = host.events.filter { it.startsWith("kompis") }
        assertEquals(listOf("kompis En hare er et dyr.", "kompis  Den er rask. Og stor!"), spoken)
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
        assertTrue("Det er søndag 25. oktober 2026, og klokka er 14:32." in messages[0]["content"]!!.jsonPrimitive.content)
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
        assertEquals("Det var en gang.", roles[roles.size - 2]["content"]!!.jsonPrimitive.content)
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
    fun shortSentencesAreBatchedAfterFirstAndFlushedAtEnd() {
        openAndGoToListening()
        say("Fortell")
        chunk("Første. ")
        assertEquals("Første.", spokenInput("s0"))
        chunk("Andre. Tredje. ")
        assertEquals(1, bridge.speechBodies.size)
        chunk("Fjerde. ")
        assertEquals("Andre. Tredje. Fjerde.", spokenInput("s1"))
        chunk("Siste")
        chat.onDone(500)
        assertEquals("Siste", spokenInput("s2"))
        assertTrue(Json.parseToJsonElement(bridge.speechBodies["s0"]!!).jsonObject["instructions"]!!.jsonPrimitive.content.contains("østnorsk"))
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
        assertTrue(body["instructions"]!!.jsonPrimitive.content.endsWith(" rolig"))
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
    fun limitedAutoLanguageIsSentAgainAsNorwegianBeforeTheAnswer() {
        store.update { it.copy(settings = it.settings.copy(sttLanguage = SttLanguage.Limited.id)) }
        openAndGoToListening()
        pipeline.talkStart()
        clockMillis += 1_000
        pipeline.talkStop()
        assertEquals(
            SttConfig("or-key", "mistralai/voxtral-mini-transcribe-3-0", null, verbose = true),
            Json.decodeFromString(SttConfig.serializer(), bridge.sttStopConfig!!),
        )

        // Svensk betyr nesten alltid feilhørt norsk, så språkmodellen får ikke se «tusen tack».
        clockMillis += 500
        stt.onTranscribed("""{"text":"Tusen tack","language":"sv"}""", 2.0, 64_000, 480)
        assertNull(bridge.chatSentWith)
        assertEquals("resend", bridge.pipelineCalls.last())
        assertEquals("no", Json.decodeFromString(SttConfig.serializer(), bridge.sttResendConfig!!).language)
        assertTrue(host.events.none { it.startsWith("user") })

        clockMillis += 300
        stt.onTranscribed("""{"text":"Tusen takk","language":"no"}""", 2.0, 64_000, 280)
        assertTrue("user Tusen takk" in host.events)
        val messages = lastRequest()["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals("Tusen takk", messages.last()["content"]!!.jsonPrimitive.content)

        chunk("Bare hyggelig! ")
        speech.onItemStarted("s0")
        assertEquals(800L, host.timings!!.sttMillis, "begge sendingene regnes med")
    }

    @Test
    fun aChosenLanguageIsSentOnlyOnce() {
        openAndGoToListening()
        pipeline.talkStart()
        pipeline.talkStop()
        stt.onTranscribed("""{"text":"Hej","language":"sv"}""", 2.0, 64_000, 480)
        assertNull(bridge.sttResendConfig)
        assertTrue("user Hej" in host.events)
    }

    @Test
    fun successfulToolResultIsSentBackBeforeSay() {
        val tool = object : no.jodal.salat.apps.kompis.tools.KompisTool by no.jodal.salat.apps.kompis.tools.SayTool {
            override val name = "test_action"
            override suspend fun execute(args: kotlinx.serialization.json.JsonObject): no.jodal.salat.apps.kompis.tools.ToolResult {
                assertEquals("06:30", args["time"]!!.jsonPrimitive.content)
                return no.jodal.salat.apps.kompis.tools.ToolResult("Opprettet")
            }
        }
        pipeline = OpenRouterPipeline(store, bridge, host, clock = { clockMillis }, tools = no.jodal.salat.apps.kompis.tools.KompisTools(listOf(no.jodal.salat.apps.kompis.tools.SayTool, tool)))
        openAndGoToListening()
        say("Sett alarm")
        chat.onEvent("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"action1","function":{"name":"test_action","arguments":"{\"time\":\"06:30\"}"}}]}}]}""", 100)
        chat.onDone(200)
        val messages = lastRequest()["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals("action1", messages[messages.lastIndex - 1]["tool_calls"]!!.jsonArray.single().jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("action1", messages.last()["tool_call_id"]!!.jsonPrimitive.content)
        assertTrue("Opprettet" in messages.last()["content"]!!.jsonPrimitive.content)
        toolEvent("""{"text":"Alarmen er satt."}""")
        chat.onDone(200)
        assertEquals("Alarmen er satt.", spokenInput("s0"))
    }

    @Test
    fun malformedArgumentsAreReturnedAsAnError() {
        openAndGoToListening()
        say("Hei")
        chat.onEvent("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"bad","function":{"name":"rise_list","arguments":"{"}}]}}]}""", 100)
        chat.onDone(200)
        val result = lastRequest()["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(Json.parseToJsonElement(result).jsonObject["isError"]!!.jsonPrimitive.content == "true")
    }

    @Test
    fun unknownToolReturnsAnErrorAndRequestsAnotherRound() {
        openAndGoToListening()
        say("Hei")
        val original = chat
        chat.onEvent("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call1","function":{"name":"unknown","arguments":"{}"}}]}}]}""", 100)
        chat.onDone(200)
        assertTrue(original !== chat)
        val messages = lastRequest()["messages"]!!.jsonArray
        assertEquals("tool", messages.last().jsonObject["role"]!!.jsonPrimitive.content)
        assertTrue(messages.last().jsonObject["content"]!!.jsonPrimitive.content.contains("Ukjent verktøy"))
        toolEvent("""{"text":"Prøv igjen."}""")
        chat.onDone(200)
        assertTrue(bridge.speechBodies.isNotEmpty())
    }

    @Test
    fun stopsAfterFourToolRounds() {
        openAndGoToListening()
        say("Hei")
        repeat(4) {
            chat.onEvent("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"x","function":{"name":"unknown","arguments":"{}"}}]}}]}""", 100)
            chat.onDone(200)
        }
        assertTrue(spokenInput("s0").contains("fire forsøk"))
    }

    private fun toolEvent(arguments: String) = chat.onEvent(
        """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"say","arguments":${JsonPrimitive(arguments)}}}]}}]}""",
        300,
    )

    private fun spokenInput(id: String) = Json.parseToJsonElement(bridge.speechBodies[id]!!).jsonObject["input"]!!.jsonPrimitive.content

    @Test
    fun theModelAnswersWithTheSayToolAndMisheardWordsAreCorrectedInTheMemory() {
        openAndGoToListening()
        say("Tusen tack")
        val request = lastRequest()
        assertEquals("required", request["tool_choice"]!!.jsonPrimitive.content)

        toolEvent("""{"understood":"Tusen takk","language":"no",""")
        assertTrue("debug Forstod: Tusen takk  (hørt som: Tusen tack)" in host.events)
        toolEvent(""""text":"Bare hyggelig! """)
        toolEvent("""Hyggelig å høre."}""")
        chat.onDone(500)
        assertEquals(setOf("s0", "s1"), bridge.speechBodies.keys)
        assertEquals("Bare hyggelig!", spokenInput("s0"))
        assertEquals("Hyggelig å høre.", spokenInput("s1"))
        assertEquals("finish", bridge.pipelineCalls.last())

        speech.onItemStarted("s0")
        speech.onItemStarted("s1")
        speech.onFinished()

        // Minnet har det brukeren mente, så språkmodellen ikke ser «tack» igjen.
        say("Takk igjen")
        val messages = lastRequest()["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(
            listOf("Tusen takk", "Bare hyggelig! Hyggelig å høre.", "Takk igjen"),
            messages.drop(1).map { it["content"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun theVoiceStaysTheSameWhateverTheModelAnswers() {
        store.update { it.copy(settings = it.settings.copy(ttsInstructions = "rolig")) }
        ModelCache.tts = listOf(
            OpenRouterModel(
                "mistralai/voxtral-mini-tts-3-0", "Mistral: Voxtral TTS 3", 1,
                voices = listOf("voice_3150", "en_paul_happy", "en_paul_neutral"),
            ),
        )
        openAndGoToListening()
        say("Can you speak English?")
        toolEvent("""{"understood":"Can you speak English?","language":"en","text":"Sure! I can."}""")
        chat.onDone(400)

        val first = Json.parseToJsonElement(bridge.speechBodies["s0"]!!).jsonObject
        assertEquals("Sure!", first["input"]!!.jsonPrimitive.content)
        assertEquals("voice_3150", first["voice"]!!.jsonPrimitive.content)
        assertTrue(first["instructions"]!!.jsonPrimitive.content.endsWith(" rolig"))

        // Samme stemme på norsk.
        speech.onFinished()
        say("Takk")
        bridge.speechBodies.clear()
        toolEvent("""{"understood":"Takk","language":"no","text":"Bare hyggelig"}""")
        chat.onDone(400)
        val norwegian = Json.parseToJsonElement(bridge.speechBodies["s0"]!!).jsonObject
        assertEquals("Bare hyggelig", norwegian["input"]!!.jsonPrimitive.content)
        assertEquals("voice_3150", norwegian["voice"]!!.jsonPrimitive.content)
        assertTrue(norwegian["instructions"]!!.jsonPrimitive.content.endsWith(" rolig"))
    }

    @Test
    fun plainTextAnswersStillWork() {
        openAndGoToListening()
        say("Hei")
        chunk("Hei på deg. ")
        assertEquals("Hei på deg.", spokenInput("s0"))
        assertTrue(host.events.none { it.startsWith("debug") })
    }

    @Test
    fun formatsTheTimings() {
        assertEquals(
            "Tale til tekst 210 ms  ·  Språkmodell 400 ms  ·  Stemme 240 ms  ·  Hørt etter 700 ms  ·  ferdig 3,2 s",
            formatTimings(TurnTimings(210, 400, 240, 700, 3200)),
        )
        assertEquals("Tale til tekst 210 ms", formatTimings(TurnTimings(210, null, null, null)))
    }

    // Håndfri modus.

    private fun handsFree(route: String = "iPhone-mikrofon") {
        store.update { it.copy(settings = it.settings.copy(handsFree = true)) }
        pipeline.start(emptyList())
        stt.onMicrophone(route)
        host.events.clear()
        bridge.pipelineCalls.clear()
    }

    private fun levels(level: Float, millis: Long) {
        repeat((millis / 60).toInt()) {
            clockMillis += 60
            stt.onLevel(level)
        }
    }

    @Test
    fun handsFreeRecordsAndSendsOnItsOwn() {
        handsFree()
        levels(0.05f, 600)
        assertEquals(emptyList(), bridge.pipelineCalls)

        levels(0.7f, 600)
        assertEquals(listOf("record"), bridge.pipelineCalls)
        assertTrue("status recording" in host.events)

        levels(0.05f, 900)
        assertEquals(listOf("record", "send"), bridge.pipelineCalls)
        assertTrue("status thinking" in host.events)
    }

    @Test
    fun handsFreeCountsTheTimeFromWhenYouStoppedTalking() {
        handsFree()
        levels(0.05f, 600)
        levels(0.7f, 600)
        // Med nivåer hvert 60. ms sendes opptaket etter 780 ms stillhet.
        levels(0.05f, 780)
        stt.onTranscribed("""{"text":"Hei"}""", 2.0, 64_000, 210)
        chunk("Hei!")
        chat.onDone(500)
        clockMillis += 300
        speech.onItemStarted("s0")
        // Tiden regnes fra siste ord: de 700 ms stillhet det ble ventet på, og så 300 ms.
        assertEquals(1_000L, host.timings!!.heardAfterMillis)
    }

    @Test
    fun handsFreeIgnoresACoughThatGivesNoText() {
        handsFree()
        levels(0.05f, 600)
        levels(0.7f, 600)
        levels(0.05f, 900)
        stt.onTranscribed("""{"text":""}""", 1.0, 32_000, 200)
        assertTrue(host.events.none { it.startsWith("notice") }, host.events.toString())
        assertTrue("status listening" in host.events)
    }

    @Test
    fun handsFreeInterruptsKompisWhenYouTalk() {
        handsFree()
        say("Fortell om haren")
        chunk("Haren er rask. ")
        speech.onItemStarted("s0")
        host.events.clear()
        bridge.pipelineCalls.clear()

        levels(0.9f, 240)
        assertEquals(emptyList(), bridge.pipelineCalls, "for kort til å være deg")
        levels(0.05f, 600)
        levels(0.9f, 480)
        assertTrue(bridge.chatCancelled)
        assertEquals(listOf("speech stop", "record"), bridge.pipelineCalls)
        assertTrue("interrupted" in host.events)
    }

    @Test
    fun withoutEchoCancellationOnlyATapInterruptsKompis() {
        handsFree("iPhone-mikrofon, uten ekkodemping")
        say("Fortell om haren")
        chunk("Haren er rask. ")
        speech.onItemStarted("s0")
        bridge.pipelineCalls.clear()

        levels(0.9f, 1_200)
        assertEquals(emptyList(), bridge.pipelineCalls)
        pipeline.tap()
        assertEquals(listOf("speech stop"), bridge.pipelineCalls)
    }

    @Test
    fun aTapInHandsFreeStartsAndSends() {
        handsFree()
        pipeline.tap()
        assertEquals(listOf("record"), bridge.pipelineCalls)
        pipeline.tap()
        assertEquals(listOf("record", "send"), bridge.pipelineCalls)
    }

    @Test
    fun turningHandsFreeOffSendsWhatIsBeingRecorded() {
        handsFree()
        levels(0.05f, 600)
        levels(0.7f, 600)
        store.update { it.copy(settings = it.settings.copy(handsFree = false)) }
        pipeline.handsFreeChanged()
        assertEquals(listOf("record", "send"), bridge.pipelineCalls)
    }
}
