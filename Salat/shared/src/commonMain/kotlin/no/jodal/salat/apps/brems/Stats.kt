package no.jodal.salat.apps.brems

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Hvor langt tilbake statistikken ser. */
enum class StatsRange(val days: Int, val label: String, val previousLabel: String) {
    Week(7, "Uke", "forrige uke"),
    Month(30, "Måned", "forrige måned"),
}

/** Tid og åpninger for én app i perioden. */
data class AppStats(val app: String, val millis: Long, val opens: Int)

/** Hvor ofte man lot være etter en slags pause. */
data class PauseStats(val total: Int, val stopped: Int) {
    val rate: Float get() = if (total == 0) 0f else stopped.toFloat() / total
}

/** Alt statistikksiden viser for en periode. */
data class RangeStats(
    val range: StatsRange,
    /** Hver dag i perioden, eldste først. */
    val days: List<DayUsage>,
    val totalMillis: Long,
    /** Samme tall for perioden før, eller `null` hvis Brems ikke har data så langt tilbake. */
    val previousTotalMillis: Long?,
    val opens: Int,
    val apps: List<AppStats>,
    /** Grunnene man har valgt i pausene, oftest først. */
    val reasons: List<Pair<String, Int>>,
    /** Den korte pausen ved åpning. */
    val openPauses: PauseStats,
    /** De lange pausene, etter grensen eller målet. */
    val longPauses: PauseStats,
    /** Snitt minutter per dag i hver av døgnets timer. */
    val hourly: List<Float>,
    /** Snitt minutter per ukedag, mandag først. */
    val weekdays: List<Float>,
    val sessions: Int,
    val averageSessionMillis: Long,
    val longestSession: Session?,
    /** Økter som gikk over grensen. */
    val overLimit: Int,
    /** De siste øktene i perioden, nyeste først. */
    val recentSessions: List<Session> = emptyList(),
) {
    val averageMillis: Long get() = totalMillis / range.days

    /** Endring fra perioden før, f.eks. -0.2 for 20 % mindre. */
    val trend: Float? get() = previousTotalMillis?.takeIf { it > 0 }?.let { (totalMillis - it).toFloat() / it }

    val allPauses: PauseStats get() = PauseStats(openPauses.total + longPauses.total, openPauses.stopped + longPauses.stopped)
}

fun BremsData.stats(range: StatsRange, now: Long, zone: TimeZone): RangeStats {
    val today = today(now, zone)
    val first = today.minus(DatePeriod(days = range.days - 1))
    val from = first.atStartOfDayIn(zone).toEpochMilliseconds()
    val to = today.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds()
    val dates = List(range.days) { first.plus(DatePeriod(days = it)) }
    val days = dates.map { usageOn(it, now, zone) }

    // Perioden før, bare hvis Brems har hørt noe fra den tiden.
    val previousFirst = first.minus(DatePeriod(days = range.days))
    val earliest = (opens.map { it.atMillis } + sessions.map { it.startMillis }).minOrNull()
    val previous = if (earliest != null && earliest < from) {
        List(range.days) { usageOn(previousFirst.plus(DatePeriod(days = it)), now, zone).millis }.sum()
    } else {
        null
    }

    val apps = days.flatMap { it.apps }.groupBy({ it.first }, { it.second })
        .map { (app, parts) -> AppStats(app, parts.sum(), opens.count { it.app == app && it.atMillis in from until to }) }
        .sortedByDescending { it.millis }

    val rangePauses = pauses.filter { it.startMillis in from until to && it.outcome != null }
    fun pauseStats(list: List<Pause>) = PauseStats(list.size, list.count { it.outcome == PauseOutcome.Stopped })

    val hourly = dates.map { hourlyMinutes(it, now, zone) }
        .fold(List(24) { 0f }) { sum, day -> sum.zip(day) { a, b -> a + b } }
        .map { it / range.days }

    val weekdayTotals = FloatArray(7)
    val weekdayCounts = IntArray(7)
    days.forEach {
        val index = it.date.dayOfWeek.isoDayNumber - 1
        weekdayTotals[index] += it.millis / 60_000f
        weekdayCounts[index]++
    }

    val rangeSessions = sessions.filter { it.startMillis in from until to }
    val limit = settings.limitMinutes * 60_000L
    return RangeStats(
        range = range,
        days = days,
        totalMillis = days.sumOf { it.millis },
        previousTotalMillis = previous,
        opens = days.sumOf { it.opens },
        apps = apps,
        reasons = rangePauses.mapNotNull { it.why }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second },
        openPauses = pauseStats(rangePauses.filter { it.kind == PauseKind.Open }),
        longPauses = pauseStats(rangePauses.filter { it.kind != PauseKind.Open }),
        hourly = hourly,
        weekdays = List(7) { if (weekdayCounts[it] == 0) 0f else weekdayTotals[it] / weekdayCounts[it] },
        sessions = rangeSessions.size,
        averageSessionMillis = if (rangeSessions.isEmpty()) 0 else rangeSessions.sumOf { it.millis } / rangeSessions.size,
        longestSession = rangeSessions.maxByOrNull { it.millis },
        overLimit = rangeSessions.count { it.millis >= limit },
        recentSessions = rangeSessions.sortedByDescending { it.startMillis }.take(RecentSessions),
    )
}

/** Timen med mest bruk, f.eks. 21 for kl. 21–22, eller `null` hvis det ikke er noen bruk. */
fun RangeStats.peakHour(): Int? = hourly.indices.maxByOrNull { hourly[it] }?.takeIf { hourly[it] > 0f }

/** Ukedagen med mest bruk, 0 for mandag, eller `null`. */
fun RangeStats.peakWeekday(): Int? = weekdays.indices.maxByOrNull { weekdays[it] }?.takeIf { weekdays[it] > 0f }


/** Så mange av de siste øktene vises i statistikken. */
const val RecentSessions = 30
