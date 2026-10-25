package no.jodal.salat.apps.kompis

/**
 * Hører når noen snakker og når de er ferdige, ut fra lydnivået fra mikrofonen. Brukes i håndfri modus,
 * der ingen trykker på en knapp.
 *
 * Bakgrunnsstøyen følges hele tiden, så det som regnes som tale er noe tydelig over den. Mens Kompis
 * snakker ([strict]) kreves det mer og lenger, så rester av stemmen til Kompis ikke avbryter seg selv.
 */
internal class VoiceActivity(private val clock: () -> Long) {
    enum class Event { None, SpeechStarted, SpeechEnded }

    /** Det vanlige nivået uten tale, på samme skala som nivåene fra mikrofonen (0 til 1). */
    private var floor = InitialFloor
    private var speaking = false
    private var loudSince = NotSet
    private var quietSince = NotSet
    private var startedAt = 0L

    /** Hvor lenge det må være stille før en ytring regnes som ferdig. */
    val silenceMillis: Long get() = EndSilenceMillis

    fun update(level: Float, strict: Boolean = false): Event {
        val now = clock()
        if (!speaking) {
            // Støynivået følger med ned raskt og opp sakte, så en lang lyd ikke blir til støy.
            floor = if (level < floor) floor + (level - floor) * 0.15f else floor + (level - floor) * 0.02f
            val margin = if (strict) StrictMargin else StartMargin
            val needed = if (strict) StrictMillis else StartMillis
            if (level <= floor + margin) {
                loudSince = NotSet
                return Event.None
            }
            if (loudSince == NotSet) loudSince = now
            if (now - loudSince < needed) return Event.None
            speaking = true
            quietSince = NotSet
            startedAt = loudSince
            return Event.SpeechStarted
        }
        if (level > floor + ContinueMargin) {
            quietSince = NotSet
        } else {
            if (quietSince == NotSet) quietSince = now
            if (now - quietSince >= EndSilenceMillis) return end()
        }
        // En ytring som aldri tar slutt, er støy. Da er det nivået det vanlige.
        if (now - startedAt > MaxUtteranceMillis) {
            floor = maxOf(floor, level)
            return end()
        }
        return Event.None
    }

    /** Glemmer en ytring som pågår, f.eks. når en tur er ferdig. Støynivået beholdes. */
    fun reset() {
        speaking = false
        loudSince = NotSet
        quietSince = NotSet
    }

    private fun end(): Event {
        reset()
        return Event.SpeechEnded
    }

    private companion object {
        const val NotSet = -1L
        const val InitialFloor = 0.1f
        const val StartMargin = 0.18f
        const val StartMillis = 150L
        const val StrictMargin = 0.32f
        const val StrictMillis = 350L
        const val ContinueMargin = 0.10f
        const val EndSilenceMillis = 700L
        const val MaxUtteranceMillis = 45_000L
    }
}
