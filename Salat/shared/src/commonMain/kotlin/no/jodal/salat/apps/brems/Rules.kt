package no.jodal.salat.apps.brems

import kotlin.random.Random
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

/** Det som skal skje når en app åpnes. */
sealed interface Decision {
    /** Appen kan brukes med en gang. */
    data object Go : Decision

    /** Brems skal vise [pause] først. */
    data class Wait(val pause: Pause) : Decision
}

/**
 * Reglene i Brems, uten noe fra iOS, så de kan testes. Snarveier sier fra når en app åpnes og lukkes,
 * og alt annet regnes ut fra det.
 */
object BremsRules {
    /** Så lenge man kan gå tilbake til appen uten ny pause, etter at pausen er over. */
    const val PassMillis = 2 * 60_000L

    /**
     * Sier ikke Snarveier fra om at appen ble lukket, f.eks. fordi skjermen ble låst, regnes økta
     * som ferdig etter så lang tid.
     */
    const val MaxSessionMillis = 60 * 60_000L

    /** «Lukket» rett etter «åpnet» kommer fra appen man forlot, ikke den som nettopp ble åpnet. */
    const val CloseGraceMillis = 3_000L

    /** Så lenge historikken tas vare på. */
    const val KeepMillis = 120L * 24 * 60 * 60_000L

    fun opened(
        data: BremsData,
        app: String?,
        now: Long,
        zone: TimeZone = TimeZone.currentSystemDefault(),
        random: Random = Random,
    ): Pair<BremsData, Decision> {
        val name = app?.trim().orEmpty()
        var d = endActive(data, now).let { it.copy(opens = it.opens + OpenEvent(name, now)) }
        if (!d.settings.enabled) return startSession(d, name, now) to Decision.Go

        val current = d.pauses.lastOrNull()
        if (current != null && current.outcome == null) {
            // Pausen pågår fortsatt, uansett hvilken app man prøver å åpne.
            if (now < current.untilMillis) return d to Decision.Wait(current)
            // Pausen er over, men man gikk rett tilbake uten å trykke på noe.
            if (now < current.untilMillis + PassMillis) {
                return startSession(resolve(d, PauseOutcome.Continued), name, now) to Decision.Go
            }
            d = resolve(d, PauseOutcome.Stopped)
        }

        val pass = d.pass
        if (pass != null && now < pass.untilMillis && (pass.app == name || pass.app.isEmpty() || name.isEmpty())) {
            return startSession(d.copy(pass = null), name, now) to Decision.Go
        }

        val (kind, seconds) = pauseFor(d, name, now, zone)
        if (seconds <= 0) return startSession(d, name, now) to Decision.Go
        val quote = d.settings.quotes.takeIf { d.settings.showQuote && it.isNotEmpty() }?.random(random)
        val pause = Pause(
            id = "p$now",
            app = name,
            kind = kind,
            startMillis = now,
            untilMillis = now + seconds * 1000L,
            quote = quote,
        )
        return d.copy(pauses = d.pauses + pause, pass = null) to Decision.Wait(pause)
    }

    fun closed(data: BremsData, now: Long): BremsData {
        val active = data.active ?: return data
        if (now - active.startMillis < CloseGraceMillis) return data
        return endActive(data, now)
    }

    /**
     * Hvor lang pause neste åpning av [app] skal gi. Gikk forrige økt over grensen, teller tiden siden man
     * lukket appen. Er dagens mål for appen nådd, blir det den lange pausen hver gang.
     */
    fun pauseFor(data: BremsData, app: String, now: Long, zone: TimeZone): Pair<PauseKind, Int> {
        val settings = data.settings
        val last = data.sessions.lastOrNull()
        val limitLeft = if (last != null && last.millis >= settings.limitMinutes * 60_000L) {
            settings.limitPauseSeconds - ((now - last.endMillis) / 1000).toInt()
        } else {
            0
        }
        val goal = if (data.reachedGoal(app, now, zone) != null) settings.limitPauseSeconds else 0
        return when {
            goal >= limitLeft && goal > settings.openPauseSeconds -> PauseKind.Goal to goal
            limitLeft > settings.openPauseSeconds -> PauseKind.Limit to limitLeft
            else -> PauseKind.Open to settings.openPauseSeconds
        }
    }

    /** Pausen som pågår eller er ferdig, men ikke besvart. */
    fun currentPause(data: BremsData, now: Long): Pause? =
        data.pauses.lastOrNull()?.takeIf { it.outcome == null && now < it.untilMillis + PassMillis }

    /** Man har ventet og vil tilbake til appen. */
    fun continueToApp(data: BremsData, now: Long): BremsData {
        val pause = data.pauses.lastOrNull() ?: return data
        return resolve(data, PauseOutcome.Continued).copy(pass = Pass(pause.app, now + PassMillis))
    }

    fun stop(data: BremsData): BremsData = resolve(data, PauseOutcome.Stopped)

    fun answerWhy(data: BremsData, why: String?): BremsData =
        data.copy(pauses = data.pauses.dropLast(1) + listOfNotNull(data.pauses.lastOrNull()?.copy(why = why)))

    /** Varslene en økt i [app] skal gi, i minutter etter at den startet. */
    fun notifications(settings: BremsSettings, app: String): List<SessionNotice> {
        val name = appName(app)
        val notices = mutableListOf<SessionNotice>()
        if (settings.reminderMinutes in 1 until settings.limitMinutes) {
            notices += SessionNotice(settings.reminderMinutes, "${formatMinutes(settings.reminderMinutes)} i $name", "Er det fortsatt det du vil bruke tiden på?")
        }
        notices += SessionNotice(
            settings.limitMinutes,
            "${formatMinutes(settings.limitMinutes).replaceFirstChar { it.uppercase() }}. Tid for en pause.",
            "Lukk $name og legg fra deg telefonen i ${formatSeconds(settings.limitPauseSeconds)}.",
        )
        if (settings.nagMinutes > 0) {
            for (after in NagAfterMinutes) {
                val minutes = settings.limitMinutes + after
                notices += SessionNotice(minutes, "${formatMinutes(minutes)} i $name", "Du er over grensen din. Lukk appen.")
            }
        }
        return notices
    }

    /**
     * Når de ekstra varslene kommer, i minutter etter grensen. Brems får ikke vite om en app er lukket
     * uten at Snarveier sier fra, så få varsler gir få falske alarmer hvis det glipper.
     */
    val NagAfterMinutes = listOf(2, 5, 10, 15)

    /** Fjerner det som er eldre enn [KeepMillis]. */
    fun trim(data: BremsData, now: Long): BremsData {
        val cutoff = now - KeepMillis
        return data.copy(
            opens = data.opens.filter { it.atMillis >= cutoff },
            sessions = data.sessions.filter { it.endMillis >= cutoff },
            pauses = data.pauses.filter { it.startMillis >= cutoff },
        )
    }

    private fun startSession(data: BremsData, app: String, now: Long) = data.copy(active = ActiveSession(app, now))

    private fun endActive(data: BremsData, now: Long): BremsData {
        val active = data.active ?: return data
        val end = minOf(now, active.startMillis + MaxSessionMillis)
        return data.copy(active = null, sessions = data.sessions + Session(active.app, active.startMillis, end))
    }

    private fun resolve(data: BremsData, outcome: PauseOutcome): BremsData {
        val last = data.pauses.lastOrNull() ?: return data
        if (last.outcome != null) return data
        return data.copy(pauses = data.pauses.dropLast(1) + last.copy(outcome = outcome))
    }
}

/** Et varsel [minutes] minutter ut i en økt. */
@Serializable
data class SessionNotice(val minutes: Int, val title: String, val body: String)

/** «1 sekund», «45 sekunder», «1 minutt», «1 min 30 sek». */
fun formatSeconds(seconds: Int): String = when {
    seconds == 1 -> "1 sekund"
    seconds < 60 -> "$seconds sekunder"
    seconds % 60 == 0 -> formatMinutes(seconds / 60)
    else -> "${seconds / 60} min ${seconds % 60} sek"
}

/** «1 minutt», «10 minutter». */
fun formatMinutes(minutes: Int): String = if (minutes == 1) "1 minutt" else "$minutes minutter"
