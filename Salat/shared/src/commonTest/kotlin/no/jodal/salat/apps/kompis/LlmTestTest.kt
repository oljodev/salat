package no.jodal.salat.apps.kompis

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.jodal.salat.FakeBridge

class LlmTestTest {
    private val bridge = FakeBridge
    private lateinit var store: KompisStore
    private lateinit var test: LlmTest
    private val now = LocalDateTime(2026, 10, 25, 14, 32)

    @BeforeTest
    fun setUp() {
        bridge.chatSentWith = null
        bridge.chatCancelled = false
        bridge.openRouterGets.clear()
        bridge.openRouterResult = null
        store = KompisStore.get(bridge)
        store.saveOpenRouterKey("or-key")
        store.update { KompisData() }
        test = LlmTest(store, bridge, now = { now })
    }

    private fun sentBody(): JsonObject = Json.parseToJsonElement(bridge.chatSentWith!!).jsonObject

    @Test
    fun sendsDeepSeekOnWaferThenBaseTenWithoutReasoning() {
        test.send("Hei")
        val body = sentBody()
        assertEquals("deepseek/deepseek-v4-flash-0731", body["model"]!!.jsonPrimitive.content)
        assertEquals("true", body["stream"]!!.jsonPrimitive.content)
        assertEquals("""{"effort":"none"}""", body["reasoning"].toString())
        assertEquals("""{"order":["wafer","baseten"],"allow_fallbacks":false}""", body["provider"].toString())

        val messages = body["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user"), messages.map { it["role"]!!.jsonPrimitive.content })
        assertTrue(messages[0]["content"]!!.jsonPrimitive.content.endsWith("Det er søndag 25. oktober 2026, og klokka er 14:32."))
        assertEquals("Hei", messages[1]["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun measuresTheStreamingAnswer() {
        test.send("Hva er en hare?")
        assertTrue(test.isStreaming)
        test.onEvent("""{"provider":"Wafer","choices":[{"delta":{"role":"assistant","content":""}}]}""", 180)
        test.onEvent("""{"choices":[{"delta":{"content":"En hare"}}]}""", 240)
        test.onEvent("""{"choices":[{"delta":{"content":" er et dyr."}}]}""", 300)
        assertEquals("En hare er et dyr.", test.draft)
        test.onEvent("""{"choices":[{"delta":{"content":" Den er rask."}}]}""", 350)
        test.onEvent(
            """{"choices":[{"delta":{"content":""},"finish_reason":"stop"}],"usage":{"prompt_tokens":1200,"completion_tokens":11,""" +
                """"cost":0.00002,"prompt_tokens_details":{"cached_tokens":1024},"completion_tokens_details":{"reasoning_tokens":0}}}""",
            360,
        )
        test.onDone(361)

        assertFalse(test.isStreaming)
        val answer = test.messages.last()
        assertEquals(Role.Kompis, answer.role)
        assertEquals("En hare er et dyr. Den er rask.", answer.text)
        val stats = answer.stats!!
        assertEquals("Wafer", stats.provider)
        assertEquals(240L, stats.firstTokenMillis)
        assertEquals(350L, stats.firstSentenceMillis, "første setning er ferdig når neste begynner")
        assertEquals(361L, stats.totalMillis)
        assertEquals(1024, stats.cachedTokens)
        assertEquals(0.00002, stats.costUsd)
        assertEquals(solidRate(11, 361 - 240), stats.tokensPerSecond)
    }

    private fun solidRate(tokens: Int, millis: Long) = (tokens / (millis / 1000.0)).toInt()

    @Test
    fun sendsTheWholeConversation() {
        test.send("Hei")
        test.onEvent("""{"choices":[{"delta":{"content":"Hei på deg!"}}]}""", 200)
        test.onDone(220)
        test.send("Hvordan går det?")
        val roles = sentBody()["messages"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content }
        assertEquals(listOf("system", "user", "assistant", "user"), roles)
    }

    @Test
    fun showsErrorsInTheMiddleOfTheStream() {
        test.send("Hei")
        test.onEvent("""{"error":{"code":502,"message":"Wafer er nede"},"choices":[]}""", 400)
        assertFalse(test.isStreaming)
        assertTrue(bridge.chatCancelled)
        assertEquals("Wafer er nede", test.message)
    }

    @Test
    fun stoppingKeepsWhatCameWithoutStats() {
        test.send("Fortell en lang historie")
        test.onEvent("""{"choices":[{"delta":{"content":"Det var en gang"}}]}""", 250)
        test.stop()
        assertFalse(test.isStreaming)
        assertEquals("Det var en gang", test.messages.last().text)
        assertNull(test.messages.last().stats)
        // Hendelser etter at svaret er stoppet, blir ikke med.
        test.onEvent("""{"choices":[{"delta":{"content":" en hare"}}]}""", 300)
        test.onDone(310)
        assertEquals(2, test.messages.size)
    }

    @Test
    fun otherModelsLetOpenRouterPickAndRespectReasoning() {
        bridge.openRouterResult = """{"data":[
            {"id":"a/thinks","name":"A: Thinks","created":2,"reasoning":{"mandatory":true,"supported_efforts":["high"]}},
            {"id":"b/toggle","name":"B: Toggle","created":1,"reasoning":{"mandatory":false,"supported_efforts":["high","low"]}},
            {"id":"c/plain","name":"C: Plain","created":3}
        ]}""" to null
        test.loadModels()

        test.selectModel("a/thinks")
        assertEquals(emptyList(), test.providerOrder)
        test.send("Hei")
        assertNull(sentBody()["reasoning"])
        assertNull(sentBody()["provider"])
        test.onDone(100)

        test.selectModel("b/toggle")
        test.send("Hei")
        assertEquals("""{"enabled":false}""", sentBody()["reasoning"].toString())
        test.onDone(100)

        test.selectModel("c/plain")
        test.send("Hei")
        assertNull(sentBody()["reasoning"])
        test.onDone(100)

        test.selectModel(DefaultLlmModel)
        assertEquals(DefaultLlmProviders, test.providerOrder)
    }

    @Test
    fun choosesProvidersInOrder() {
        store.update { it.copy(settings = it.settings.copy(llmProviders = emptyList())) }
        test.toggleProvider("baseten")
        test.toggleProvider("wafer")
        assertEquals(listOf("baseten", "wafer"), test.providerOrder)
        test.toggleProvider("baseten")
        assertEquals(listOf("wafer"), test.providerOrder)
    }

    @Test
    fun parsesProvidersOnceEach() {
        val providers = OpenRouter.parseProviders(
            """{"data":{"id":"x","endpoints":[
                {"provider_name":"Wafer","tag":"wafer/fast"},
                {"provider_name":"BaseTen","tag":"baseten/fp8"},
                {"provider_name":"BaseTen","tag":"baseten/fp32"},
                {"provider_name":"Venice","tag":"venice"}
            ]}}""",
        )
        assertEquals(
            listOf(OpenRouterProvider("wafer", "Wafer"), OpenRouterProvider("baseten", "BaseTen"), OpenRouterProvider("venice", "Venice")),
            providers,
        )
    }

    @Test
    fun splitsSentencesAsTheyStreamIn() {
        assertEquals(listOf("Hei!", "Hvordan går det?") to "Jeg har", Sentences.split("Hei! Hvordan går det? Jeg har"))
        assertEquals(emptyList<String>() to "Det koster 2.5 kroner.", Sentences.split("Det koster 2.5 kroner."))
        assertEquals(listOf("Han sa «hei.»") to "Så", Sentences.split("Han sa «hei.» Så"))
        assertFalse(Sentences.hasComplete("Hei."))
        assertTrue(Sentences.hasComplete("Hei. D"))
    }

    @Test
    fun usesThePersonalityFromTheSettings() {
        store.update { it.copy(settings = it.settings.copy(personality = "Du er en pirat.")) }
        val prompt = test.prompt()
        assertTrue(prompt.startsWith("Du er en pirat.\n\n# Situasjonen"))
        assertTrue("Ingen forkortelser" in prompt)
    }
}
