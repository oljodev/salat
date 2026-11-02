package no.jodal.salat.apps.kompis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.jodal.salat.FakeBridge

class LlmRoutingTest {
    @Test
    fun lowestLatencyKeepsPriceCapsAndOnlyUsesSelectedProvidersInBothCallers() {
        val store = KompisStore.get(FakeBridge)
        store.saveOpenRouterKey("test")
        store.update { KompisData(settings = KompisSettings(
            engine = KompisEngine.OpenRouter.id,
            llmProviders = listOf("baseten", "wafer"),
            llmRouting = LlmRouting("latency", maxInput = 0.25, maxOutput = 1.0),
        )) }
        val test = LlmTest(store, FakeBridge)
        val testBody = Json.parseToJsonElement(test.requestBody()).jsonObject
        val pipeline = OpenRouterPipeline(store, FakeBridge, object : PipelineHost {
            override fun onNotice(message: String) {}
            override fun onTimings(timings: TurnTimings) {}
            override fun onStatus(status: String) {}
            override fun onLevel(level: Float) {}
            override fun onUserText(text: String) {}
            override fun onKompisText(text: String) {}
            override fun onTurnComplete() {}
            override fun onInterrupted() {}
            override fun onAudioRoute(name: String) {}
            override fun onError(message: String) {}
            override fun onDebug(text: String) {}
        })
        FakeBridge.openRouterResult = null
        pipeline.start(emptyList())
        FakeBridge.sttListener!!.onMicrophone("iPhone")
        pipeline.talkStart()
        pipeline.talkStop()
        FakeBridge.sttListener!!.onTranscribed("""{"text":"Hei"}""", 1.0, 100, 50)
        val actual = Json.parseToJsonElement(FakeBridge.chatSentWith!!).jsonObject["provider"]!!.jsonObject
        assertEquals(testBody["provider"], actual)
        assertEquals("latency", actual["sort"]!!.jsonPrimitive.content)
        assertEquals(listOf("baseten", "wafer"), actual["only"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertFalse("order" in actual)
        assertEquals("0.25", actual["max_price"]!!.jsonObject["prompt"]!!.jsonPrimitive.content)
        assertEquals("1.0", actual["max_price"]!!.jsonObject["completion"]!!.jsonPrimitive.content)
        pipeline.stop()
    }

    private fun provider(order: List<String>, routing: LlmRouting) = Json.parseToJsonElement(
        LlmRequest.body("test/model", order, null, "Hei", emptyList(), routing = routing),
    ).jsonObject["provider"]?.jsonObject

    @Test
    fun manualOrderIsStrictAndAutoDoesNotKeepAnOldOrder() {
        val manual = provider(listOf("b", "a"), LlmRouting())!!
        assertEquals(listOf("b", "a"), manual["order"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("false", manual["allow_fallbacks"]!!.jsonPrimitive.content)
        assertNull(provider(listOf("b", "a"), LlmRouting("auto")))
        val autoCap = provider(listOf("b"), LlmRouting("auto", maxInput = 0.0))!!
        assertEquals(setOf("max_price"), autoCap.keys)
        assertEquals("0.0", autoCap["max_price"]!!.jsonObject["prompt"]!!.jsonPrimitive.content)
    }

    @Test
    fun blankPriceIsUnlimitedAndNorwegianDecimalsAndFreeOnlyAreValid() {
        assertNull(parsePriceLimit(""))
        assertTrue(validPriceLimit(""))
        assertEquals(0.25, parsePriceLimit(" 0,25 "))
        assertEquals(0.0, parsePriceLimit("0"))
        listOf("-1", "NaN", "Infinity", "hei", "1,2,3").forEach { assertFalse(validPriceLimit(it), it) }
    }

    @Test
    fun oldSettingsStillLoadAndNewRoutingSurvivesSaving() {
        val old = Json.decodeFromString(KompisSettings.serializer(), """{"llmProviders":["wafer"]}""")
        assertEquals(LlmRouting(), old.llmRouting)
        val settings = old.copy(llmRouting = LlmRouting("price", 0.5, 0.0))
        assertEquals(settings, Json.decodeFromString(KompisSettings.serializer(), Json.encodeToString(KompisSettings.serializer(), settings)))
    }
}
