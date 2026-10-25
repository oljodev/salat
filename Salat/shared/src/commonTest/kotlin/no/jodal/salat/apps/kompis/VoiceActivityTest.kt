package no.jodal.salat.apps.kompis

import kotlin.test.Test
import kotlin.test.assertEquals
import no.jodal.salat.apps.kompis.VoiceActivity.Event

class VoiceActivityTest {
    private var now = 0L
    private val activity = VoiceActivity { now }

    /** Mater inn like mange nivåer som det går [millis] på, med 60 ms mellom hver. Gir alle hendelser som kom. */
    private fun feed(level: Float, millis: Long, strict: Boolean = false): List<Event> {
        val events = mutableListOf<Event>()
        repeat((millis / 60).toInt()) {
            now += 60
            activity.update(level, strict).takeIf { it != Event.None }?.let(events::add)
        }
        return events
    }

    @Test
    fun hearsSpeechAfterAShortWhile() {
        assertEquals(emptyList(), feed(0.05f, 600))
        assertEquals(listOf(Event.SpeechStarted), feed(0.7f, 300))
    }

    @Test
    fun ignoresAShortClick() {
        feed(0.05f, 600)
        assertEquals(emptyList(), feed(0.8f, 120))
        assertEquals(emptyList(), feed(0.05f, 2_000))
    }

    @Test
    fun speechEndsAfterSilenceButNotAfterAShortPause() {
        feed(0.05f, 600)
        assertEquals(listOf(Event.SpeechStarted), feed(0.7f, 600))
        assertEquals(emptyList(), feed(0.05f, 420), "en kort pause midt i en setning")
        assertEquals(emptyList(), feed(0.7f, 600))
        assertEquals(listOf(Event.SpeechEnded), feed(0.05f, 900))
        assertEquals(emptyList(), feed(0.05f, 900), "ferdig, og ikke mer å melde")
    }

    @Test
    fun needsMoreWhileKompisIsSpeaking() {
        feed(0.05f, 600)
        assertEquals(emptyList(), feed(0.35f, 1_000, strict = true), "svakt, som en rest av stemmen")
        assertEquals(emptyList(), feed(0.8f, 240, strict = true), "for kort")
        assertEquals(emptyList(), feed(0.05f, 600, strict = true))
        assertEquals(listOf(Event.SpeechStarted), feed(0.8f, 480, strict = true))
    }

    @Test
    fun treatsSomethingThatNeverEndsAsNoise() {
        feed(0.05f, 600)
        // En vedvarende susing høres først som tale, men blir til støy, og tale over den høres fortsatt.
        assertEquals(listOf(Event.SpeechStarted, Event.SpeechEnded), feed(0.3f, 50_000))
        assertEquals(emptyList(), feed(0.3f, 600))
        assertEquals(listOf(Event.SpeechStarted), feed(0.8f, 300))
    }

    @Test
    fun givesUpOnSomethingThatNeverEnds() {
        feed(0.05f, 600)
        assertEquals(listOf(Event.SpeechStarted), feed(0.7f, 300))
        assertEquals(listOf(Event.SpeechEnded), feed(0.7f, 50_000))
    }
}
