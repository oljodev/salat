package no.jodal.salat.apps.brems

import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Bruken én dag: hvor lenge, hvor ofte, og hvor mange ganger man lot være. */
data class DayUsage(
    val date: LocalDate,
    val millis: Long,
    val opens: Int,
    val pauses: Int,
    val stopped: Int,
    /** Tiden i hver app, mest brukt først. */
    val apps: List<Pair<String, Long>>,
)

/** Bruken på [date], med økta som pågår fram til [now]. */
fun BremsData.usageOn(date: LocalDate, now: Long, zone: TimeZone): DayUsage {
    val from = date.atStartOfDayIn(zone).toEpochMilliseconds()
    val to = date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds()
    val all = sessions + listOfNotNull(active?.let { Session(it.app, it.startMillis, minOf(now, it.startMillis + BremsRules.MaxSessionMillis)) })
    val perApp = all
        .map { it.app to (minOf(it.endMillis, to) - maxOf(it.startMillis, from)).coerceAtLeast(0) }
        .filter { it.second > 0 }
        .groupBy({ it.first }, { it.second })
        .map { (app, parts) -> app to parts.sum() }
        .sortedByDescending { it.second }
    val dayPauses = pauses.filter { it.startMillis in from until to }
    return DayUsage(
        date = date,
        millis = perApp.sumOf { it.second },
        opens = opens.count { it.atMillis in from until to },
        pauses = dayPauses.size,
        stopped = dayPauses.count { it.outcome == PauseOutcome.Stopped },
        apps = perApp,
    )
}

/** Når på [date] appene var åpne, som deler av døgnet fra 0 til 1. */
fun BremsData.segmentsOn(date: LocalDate, now: Long, zone: TimeZone): List<ClosedFloatingPointRange<Float>> {
    val from = date.atStartOfDayIn(zone).toEpochMilliseconds()
    val to = date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds()
    val all = sessions + listOfNotNull(active?.let { Session(it.app, it.startMillis, minOf(now, it.startMillis + BremsRules.MaxSessionMillis)) })
    val length = (to - from).toFloat()
    return all
        .filter { it.endMillis > from && it.startMillis < to }
        .map { ((maxOf(it.startMillis, from) - from) / length)..((minOf(it.endMillis, to) - from) / length) }
}

/** Minutter i appene i hver av døgnets 24 timer på [date]. */
fun BremsData.hourlyMinutes(date: LocalDate, now: Long, zone: TimeZone): List<Float> {
    val from = date.atStartOfDayIn(zone).toEpochMilliseconds()
    val to = date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds()
    val hour = (to - from) / 24
    val all = sessions + listOfNotNull(active?.let { Session(it.app, it.startMillis, minOf(now, it.startMillis + BremsRules.MaxSessionMillis)) })
    return List(24) { h ->
        val start = from + h * hour
        val end = start + hour
        all.sumOf { (minOf(it.endMillis, end) - maxOf(it.startMillis, start)).coerceAtLeast(0) } / 60_000f
    }
}

fun yesterday(now: Long, zone: TimeZone): LocalDate = today(now, zone).minus(DatePeriod(days = 1))

/** Hvor langt dagen har kommet, fra 0 ved midnatt til 1. */
fun dayFraction(now: Long, zone: TimeZone): Float {
    val date = today(now, zone)
    val from = date.atStartOfDayIn(zone).toEpochMilliseconds()
    val to = date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds()
    return (now - from).toFloat() / (to - from)
}

fun BremsData.usageToday(now: Long, zone: TimeZone): DayUsage = usageOn(today(now, zone), now, zone)

fun today(now: Long, zone: TimeZone): LocalDate = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date

/** Til sekundet: «42 s», «12 min 5 s», «1 t 5 min 12 s». */
fun formatPrecise(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    val h = seconds / 3600
    val m = seconds / 60 % 60
    val s = seconds % 60
    return when {
        h > 0 -> "$h t $m min $s s"
        m > 0 -> "$m min $s s"
        else -> "$s s"
    }
}

/** Klokkeslettet til sekundet, f.eks. «21:43:07». */
fun formatClock(millis: Long, zone: TimeZone): String {
    val t = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}:${t.second.toString().padStart(2, '0')}"
}

/** «47 min», «1 t 5 min», «0 min». */
fun formatDuration(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes < 60) "$minutes min" else "${minutes / 60} t ${minutes % 60} min"
}

private val Weekdays = listOf("mandag", "tirsdag", "onsdag", "torsdag", "fredag", "lørdag", "søndag")
private val Months = listOf("januar", "februar", "mars", "april", "mai", "juni", "juli", "august", "september", "oktober", "november", "desember")

/** «torsdag 23. oktober». */
fun formatDay(date: LocalDate): String = "${Weekdays[date.dayOfWeek.isoDayNumber - 1]} ${date.day}. ${Months[date.month.ordinal]}"
