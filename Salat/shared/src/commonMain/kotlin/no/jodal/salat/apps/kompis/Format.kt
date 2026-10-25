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

private val ShortMonths = listOf("jan", "feb", "mar", "apr", "mai", "jun", "jul", "aug", "sep", "okt", "nov", "des")

/** Når en modell kom, f.eks. «20. okt», eller «3. mar 2025» hvis det ikke var i år. */
fun formatReleased(epochSeconds: Long, nowMillis: Long): String {
    val date = localDateTime(epochSeconds * 1000).date
    val thisYear = localDateTime(nowMillis).date.year
    return "${date.day}. ${ShortMonths[date.month.ordinal]}" + if (date.year != thisYear) " ${date.year}" else ""
}

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
