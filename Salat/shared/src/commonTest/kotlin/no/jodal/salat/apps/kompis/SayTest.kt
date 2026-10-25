package no.jodal.salat.apps.kompis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SayTest {
    private fun args(fragment: String) = ChatChunk(content = "", toolArguments = fragment, provider = null, usage = null, error = null)

    private fun plain(text: String) = ChatChunk(content = text, toolArguments = "", provider = null, usage = null, error = null)

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
            """{"understo""", """od":"Tusen takk","lang""", """uage":"no","style":"varm","te""",
            """xt":"Bare hyg""", """gelig! Og""", """ du?"}""",
        )
        val deltas = fragments.map { stream.add(args(it)) }
        assertEquals(listOf("", "", "", "Bare hyg", "gelig! Og", " du?"), deltas)
        assertEquals("Bare hyggelig! Og du?", stream.text)
        assertEquals("Tusen takk", stream.understood)
        assertEquals("no", stream.language)
        assertEquals("varm", stream.style)
        assertTrue(stream.usedTool)
    }

    @Test
    fun emptyStyleAndLanguageNamesAreCleanedUp() {
        val stream = SayStream()
        stream.add(args("""{"understood":" Hei ","language":"Norwegian","style":" ","text":"Hei"}"""))
        assertEquals("Hei", stream.understood)
        assertEquals("no", stream.language)
        assertNull(stream.style)
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

    @Test
    fun picksAVoiceForTheLanguage() {
        val voices = listOf("voice_3150", "en_paul_happy", "en_paul_neutral", "gb_jane_sad", "fr_marie_neutral", "es_ana_neutral")
        assertEquals("voice_3150", VoiceChoice.pick(voices, "voice_3150", "no"))
        assertEquals("voice_3150", VoiceChoice.pick(voices, "voice_3150", null))
        assertEquals("en_paul_neutral", VoiceChoice.pick(voices, "voice_3150", "en"))
        assertEquals("en_paul_happy", VoiceChoice.pick(voices, "en_paul_happy", "en"), "en engelsk stemme som er valgt, beholdes")
        assertEquals("es_ana_neutral", VoiceChoice.pick(voices, "voice_3150", "es"))
        assertEquals("voice_1", VoiceChoice.pick(listOf("voice_1", "voice_2"), "voice_1", "en"), "ingen stemme til språket")
        assertEquals("a", VoiceChoice.pick(emptyList(), "a", "en"))
        assertNull(VoiceChoice.pick(emptyList(), null, "en"))
        assertEquals(
            "en-US-Harper:MAI-Voice-2.1",
            VoiceChoice.pick(listOf("nb-NO-Harper:MAI-Voice-2.1", "en-US-Harper:MAI-Voice-2.1"), "nb-NO-Harper:MAI-Voice-2.1", "en"),
        )
    }
}
