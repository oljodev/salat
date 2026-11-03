package no.jodal.salat.apps.kompis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SayTest {
    private fun args(fragment: String) = ChatChunk(content = "", toolCalls = listOf(ToolCallDelta(0, name = "say", argumentsDelta = fragment)), provider = null, usage = null, error = null)

    private fun plain(text: String) = ChatChunk(content = text, toolCalls = emptyList(), provider = null, usage = null, error = null)

    @Test
    fun readsTextFieldsWhileTheyAreWritten() {
        val whole = PartialJson.strings("""{"understood":"Tusen takk","language":"no","text":"Bare hyggelig"}""")
        assertEquals("Tusen takk", whole["understood"]!!.value)
        assertTrue(whole["understood"]!!.complete)
        assertEquals("Bare hyggelig", whole["text"]!!.value)

        val partial = PartialJson.strings("""{"understood":"Tusen takk","language":"n""")
        assertTrue(partial["understood"]!!.complete)
        assertEquals("n", partial["language"]!!.value)
        assertFalse(partial["language"]!!.complete)

        assertEquals(emptyMap(), PartialJson.strings(""))
        assertEquals(emptyMap(), PartialJson.strings("""{"unders"""))
        assertEquals(emptyMap(), PartialJson.strings("""{"text" """))
    }

    @Test
    fun decodesEscapesAndStopsBeforeAnUnfinishedOne() {
        assertEquals("Hei \"du\"\næ\\", PartialJson.strings("""{"text":"Hei \"du\"\næ\\"}""")["text"]!!.value)
        assertEquals("Hei æ", PartialJson.strings("""{"text":"Hei æ"}""")["text"]!!.value)
        assertEquals("Hei ", PartialJson.strings("""{"text":"Hei \u00e""")["text"]!!.value)
        assertEquals("Hei ", PartialJson.strings("""{"text":"Hei \""")["text"]!!.value)
    }

    @Test
    fun skipsValuesThatAreNotText() {
        val fields = PartialJson.strings("""{"n":12,"list":["a","b"],"obj":{"a":"x, y"},"text":"Hei"}""")
        assertEquals(setOf("text"), fields.keys)
        assertEquals("Hei", fields["text"]!!.value)
    }

    @Test
    fun givesTheSpokenTextAsItIsWritten() {
        val stream = SayStream()
        val fragments = listOf(
            """{"understo""", """od":"Tusen takk","lang""", """uage":"no","te""",
            """xt":"Bare hyg""", """gelig! Og""", """ du?"}""",
        )
        val deltas = fragments.map { stream.add(args(it)) }
        assertEquals(listOf("", "", "", "Bare hyg", "gelig! Og", " du?"), deltas)
        assertEquals("Bare hyggelig! Og du?", stream.text)
        assertEquals("Tusen takk", stream.understood)
        assertEquals("no", stream.language)
        assertTrue(stream.usedTool)
    }

    @Test
    fun languageNamesAreCleanedUp() {
        val stream = SayStream()
        stream.add(args("""{"understood":" Hei ","language":"Norwegian","text":"Hei"}"""))
        assertEquals("Hei", stream.understood)
        assertEquals("no", stream.language)
    }

    @Test
    fun usesPlainTextWhenTheModelDoesNotUseTheTool() {
        val stream = SayStream()
        assertEquals("Hei ", stream.add(plain("Hei ")))
        assertEquals("der.", stream.add(plain("der.")))
        assertEquals("Hei der.", stream.text)
        assertFalse(stream.usedTool)
        assertNull(stream.understood)
    }

    @Test
    fun onceTheToolIsUsedPlainTextIsIgnored() {
        val stream = SayStream()
        stream.add(args("""{"text":"Hei"""))
        assertEquals("", stream.add(plain("Hei på deg")))
        assertEquals("Hei", stream.text)
    }
}
