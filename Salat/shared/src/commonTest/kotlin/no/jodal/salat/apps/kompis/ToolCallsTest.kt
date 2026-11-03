package no.jodal.salat.apps.kompis

import kotlinx.serialization.json.*
import no.jodal.salat.apps.kompis.tools.*
import kotlin.test.*

class ToolCallsTest {
    @Test
    fun assemblesInterleavedCallsByIndex() {
        val assembler = ToolCallAssembler()
        assembler.add(listOf(ToolCallDelta(1, "b", "say", "{"), ToolCallDelta(0, "a", "rise_list", "{")))
        assembler.add(listOf(ToolCallDelta(0, argumentsDelta = "}"), ToolCallDelta(1, argumentsDelta = "\"text\":\"Hei\"}")))
        assertEquals(listOf(ToolCall("a", "rise_list", "{}"), ToolCall("b", "say", "{\"text\":\"Hei\"}")), assembler.all)
    }

    @Test
    fun parsesSeparateCallsAndMetadataOnlyDeltas() {
        val chunk = parseChatChunk("""{"choices":[{"delta":{"tool_calls":[{"index":1,"id":"b","function":{"name":"say"}},{"index":0,"function":{"arguments":"{}"}}]}}]}""")!!
        assertEquals(listOf(ToolCallDelta(1, "b", "say"), ToolCallDelta(0, argumentsDelta = "{}")), chunk.toolCalls)
        assertNull(parseChatChunk("invalid"))
    }

    @Test
    fun speechUsesOnlyOneSayCallAndRetainsArgumentsBeforeName() {
        val stream = SayStream()
        fun add(vararg calls: ToolCallDelta) = stream.add(ChatChunk("", calls.toList(), null, null, null))
        assertEquals("", add(ToolCallDelta(0, name = "rise_create", argumentsDelta = "{\"text\":\"Skal ikke sies\"}")))
        assertEquals("", add(ToolCallDelta(1, argumentsDelta = "{\"text\":\"Hei")))
        assertEquals("Hei", add(ToolCallDelta(1, name = "say")))
        assertEquals("!", add(ToolCallDelta(1, argumentsDelta = "!\"}"), ToolCallDelta(2, name = "say", argumentsDelta = "{\"text\":\"Annet\"}")))
        assertEquals("Hei!", stream.text)
    }

    @Test
    fun requestIncludesRegistryAndProtocolMessages() {
        val calls = listOf(ToolCall("call1", "say", "{}"))
        val body = Json.parseToJsonElement(LlmRequest.body("model", emptyList(), null, "prompt", listOf(
            Message(Role.User, "Hei", 0),
            Message(Role.Kompis, "", 0, toolCalls = calls),
            Message(Role.Tool, "ok", 0, toolCallId = "call1"),
        ))).jsonObject
        assertEquals("required", body["tool_choice"]!!.jsonPrimitive.content)
        assertEquals("say", body["tools"]!!.jsonArray.single().jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        val messages = body["messages"]!!.jsonArray
        assertEquals("assistant", messages[2].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("call1", messages[2].jsonObject["tool_calls"]!!.jsonArray.single().jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("tool", messages[3].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("call1", messages[3].jsonObject["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals(SayTool, KompisTools()["say"])
        assertNull(KompisTools()["unknown"])
    }

    @Test
    fun requestSerializesEveryActiveTool() {
        val extra = object : KompisTool by SayTool { override val name = "extra" }
        val registry = KompisTools(listOf(SayTool, extra))
        val body = Json.parseToJsonElement(LlmRequest.body("model", emptyList(), null, "prompt", emptyList(), tools = registry.all)).jsonObject
        assertEquals(listOf("say", "extra"), body["tools"]!!.jsonArray.map {
            it.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content
        })
        assertSame(extra, registry["extra"])
    }

    @Test
    fun toolCardsAndDebugRecordsSurviveSerialization() {
        val message = Message(Role.Kompis, "Ferdig", 42,
            info = MessageInfo("OpenRouter", "model", tools = listOf(ToolRecord("rise_create", "{}", "ok", false))),
            card = ToolCard("Opprettet: Trening", "06:30"))
        assertEquals(message, Json.decodeFromString<Message>(Json.encodeToString(Message.serializer(), message)))
    }

    @Test
    fun promptIncludesTimezoneAndActiveToolHints() {
        val prompt = VoicePrompt.build("Hei", kotlinx.datetime.LocalDateTime(2030, 1, 1, 6, 30), listOf(SayTool), kotlinx.datetime.TimeZone.of("Europe/Oslo"))
        assertTrue("Europe/Oslo" in prompt)
        assertTrue(SayTool.promptHint in prompt)
        assertTrue("bekreftelse før sletting" in prompt)
    }

    @Test
    fun oldHistoryStillDecodesAndNewFieldsRoundTrip() {
        val json = Json
        val old = json.decodeFromString<Message>("""{"role":"kompis","text":"Hei","atMillis":1}""")
        assertEquals(Role.Kompis, old.role)
        assertTrue(old.toolCalls.isEmpty())
        val message = old.copy(toolCalls = listOf(ToolCall("id", "say", "{}")))
        assertEquals(message, json.decodeFromString<Message>(json.encodeToString(Message.serializer(), message)))
    }
}
