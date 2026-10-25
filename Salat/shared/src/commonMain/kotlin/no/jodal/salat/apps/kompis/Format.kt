package no.jodal.salat.apps.kompis

import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import no.jodal.salat.apps.rise.formatDate
import no.jodal.salat.apps.rise.formatTime

private fun localDateTime(millis: Long) = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())

/** F.eks. «14:32», «i går» eller «tir 21. okt». */
fun formatWhen(millis: Long, nowMillis: Long): String {
    val at = localDateTime(millis)
    val today = localDateTime(nowMillis).date
    return when (at.date) {
        today -> formatTime(at.time)
        else -> formatDate(at.date, today)
    }
}

/** F.eks. «12 min» eller «under 1 min». */
fun formatDuration(seconds: Int): String = when {
    seconds < 60 -> "under 1 min"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} t ${seconds % 3600 / 60} min"
}

/** F.eks. «04:12», for tiden en samtale har vart. */
fun formatTimer(seconds: Int): String =
    "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"

/** F.eks. «2,4 s». */
fun formatSeconds(seconds: Double): String = "${decimal(seconds, 1)} s"

/** F.eks. «77 kB». */
fun formatKilobytes(bytes: Int): String = "${(bytes + 500) / 1000} kB"

/** F.eks. «$0,00014». Lyd koster lite per forsøk, så det trengs mange desimaler. */
fun formatUsd(dollars: Double): String = "$${decimal(dollars, 5)}"

/** [value] med [decimals] desimaler og komma, som på norsk. */
private fun decimal(value: Double, decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val rounded = kotlin.math.round(value * factor).toLong()
    return "${rounded / factor},${(rounded % factor).toString().padStart(decimals, '0')}"
}

enum class Period(val label: String) { Today("I dag"), ThisWeek("Denne uken"), Earlier("Tidligere") }

fun periodOf(millis: Long, nowMillis: Long): Period {
    val date = localDateTime(millis).date
    val today = localDateTime(nowMillis).date
    val monday: LocalDate = today.minus(DatePeriod(days = today.dayOfWeek.isoDayNumber - 1))
    return when {
        date == today -> Period.Today
        date >= monday -> Period.ThisWeek
        else -> Period.Earlier
    }
}
