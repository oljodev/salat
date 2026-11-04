package no.jodal.salat.apps.brems

import kotlinx.serialization.Serializable

/** Det Brems husker: innstillingene, økta som pågår, og alt som har skjedd. */
@Serializable
data class BremsData(
    val settings: BremsSettings = BremsSettings(),
    /** Appen som er åpen nå, hvis Snarveier har sagt at den ble åpnet. */
    val active: ActiveSession? = null,
    /** Lov til å åpne en app uten pause, etter at man har ventet og trykket «Fortsett». */
    val pass: Pass? = null,
    val opens: List<OpenEvent> = emptyList(),
    val sessions: List<Session> = emptyList(),
    /** Alle pausene, den siste til slutt. */
    val pauses: List<Pause> = emptyList(),
    /** Det siste som har skjedd, så man kan se at automasjonene virker. Nyeste sist. */
    val log: List<LogEntry> = emptyList(),
    val goals: List<Goal> = emptyList(),
)

@Serializable
data class LogEntry(val atMillis: Long, val text: String)

@Serializable
data class BremsSettings(
    val enabled: Boolean = true,
    /** Pausen hver gang man åpner en app, i sekunder. 0 er ingen pause. */
    val openPauseSeconds: Int = 10,
    /** Varsel etter så mange minutter i en app. 0 er av. */
    val reminderMinutes: Int = 5,
    /** Hvor lenge en økt kan vare før man må ta en lengre pause. */
    val limitMinutes: Int = 10,
    /** Den lengre pausen, i sekunder. Tiden man er borte fra appene, teller med. */
    val limitPauseSeconds: Int = 60,
    /** Flere varsler etter grensen ([BremsRules.NagAfterMinutes]). 0 er av. */
    val nagMinutes: Int = 2,
    val breathing: Boolean = true,
    val showUsage: Boolean = true,
    val askWhy: Boolean = true,
    val showQuote: Boolean = true,
    val quotes: List<String> = DefaultQuotes,
    val reasons: List<String> = DefaultReasons,
)

val DefaultQuotes = listOf(
    "Hva ville du egentlig gjøre nå?",
    "Kjedsomhet er ikke farlig.",
    "Du kommer ikke til å huske det du scroller forbi.",
    "Se opp. Hva er rundt deg?",
    "Det kommer alltid en ny video. Denne stunden kommer ikke igjen.",
)

val DefaultReasons = listOf("Kjeder meg", "Vane", "Svare noen", "Slappe av", "Utsetter noe")

@Serializable
data class ActiveSession(val app: String, val startMillis: Long)

@Serializable
data class Session(val app: String, val startMillis: Long, val endMillis: Long) {
    val millis: Long get() = endMillis - startMillis
}

@Serializable
data class OpenEvent(val app: String, val atMillis: Long)

@Serializable
data class Pass(val app: String, val untilMillis: Long)

@Serializable
enum class PauseKind {
    /** Den korte pausen hver gang man åpner en app. */
    Open,

    /** Den lange pausen etter at forrige økt gikk over grensen. */
    Limit,

    /** Den lange pausen når dagens mål for appen er nådd. */
    Goal,
}

@Serializable
enum class PauseOutcome {
    /** Gikk tilbake til appen etter pausen. */
    Continued,

    /** Lot være. */
    Stopped,
}

@Serializable
data class Pause(
    val id: String,
    val app: String,
    val kind: PauseKind,
    val startMillis: Long,
    val untilMillis: Long,
    val quote: String? = null,
    val why: String? = null,
    val outcome: PauseOutcome? = null,
)

/** Navnet appene får når automasjonen i Snarveier ikke sier hvilken app det er. */
const val UnnamedApp = ""

/** «TikTok», eller «appen» når vi ikke vet hvilken. */
fun appName(app: String): String = app.ifEmpty { "appen" }
