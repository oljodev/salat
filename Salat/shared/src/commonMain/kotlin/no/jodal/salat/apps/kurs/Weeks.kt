package no.jodal.salat.apps.kurs

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Så mange godkjente dager trengs for at uka er nådd. */
const val WeekGoal = 5

/** Mandagen i uka [this] hører til. */
fun LocalDate.weekStart(): LocalDate = minus(DatePeriod(days = dayOfWeek.isoDayNumber - 1))

fun LocalDate.plusDays(days: Int): LocalDate = plus(DatePeriod(days = days))

/** Hvordan det gikk én uke. */
data class WeekResult(
    val monday: LocalDate,
    val approvedDays: Int,
    val points: Int,
    /** Om uka er over (søndag er passert). */
    val finished: Boolean,
    /** Om uka ble reddet med en freeze fordi den endte på 4/7. */
    val frozen: Boolean,
) {
    val reached: Boolean get() = approvedDays >= WeekGoal || frozen
    val perfect: Boolean get() = approvedDays == 7
}

/** Alt som regnes ut fra loggene. */
data class KursSummary(
    val totalPoints: Int,
    /** Uker på rad med 5/7, med inneværende uke hvis den alt er nådd. */
    val weeklyStreak: Int,
    /** Dager på rad med skjermer av før 22:00. I dag og i går kan mangle uten at rekka brytes. */
    val daily22Streak: Int,
    val availableFreezes: Int,
    /** Alle uker fra den første loggen til i dag, eldste først. */
    val weeks: List<WeekResult>,
) {
    val currentWeek: WeekResult? get() = weeks.lastOrNull()
}

/** Regner ut poeng, uker, rekker og freezes per [today]. */
fun summarize(logs: List<DayLog>, today: LocalDate): KursSummary {
    val byDate = logs.associateBy { it.date }
    val first = logs.minOfOrNull { it.date } ?: return KursSummary(0, 0, 0, 0, emptyList())
    var total = 0
    var freezes = 0
    val weeks = mutableListOf<WeekResult>()
    var monday = first.weekStart()
    val thisMonday = today.weekStart()

    fun add(points: Int) {
        val before = total / Points.PointsPerFreeze
        total += points
        val earned = total / Points.PointsPerFreeze - before
        freezes = minOf(Points.MaxFreezes, freezes + earned)
    }

    while (monday <= thisMonday) {
        val days = (0 until 7).mapNotNull { byDate[monday.plusDays(it)] }
        days.forEach { add(it.points) }
        val approved = days.count { it.isApproved }
        if (approved == 7) add(Points.PerfectWeekBonus)
        val finished = monday < thisMonday
        val frozen = finished && approved == WeekGoal - 1 && freezes > 0
        if (frozen) freezes--
        weeks += WeekResult(monday, approved, days.sumOf { it.points }, finished, frozen)
        monday = monday.plusDays(7)
    }

    val finishedWeeks = weeks.filter { it.finished }
    var streak = finishedWeeks.reversed().takeWhile { it.reached }.size
    if (weeks.last().let { !it.finished && it.reached }) streak++

    return KursSummary(total, streak, screensOffStreak(byDate, today), freezes, weeks)
}

private fun screensOffStreak(byDate: Map<LocalDate, DayLog>, today: LocalDate): Int {
    var date = today
    // I dag og i går er ofte ikke logget ennå, så de bryter ikke rekka.
    repeat(2) { if (byDate[date]?.screensOffBefore22 == null) date = date.minus(DatePeriod(days = 1)) }
    var streak = 0
    while (byDate[date]?.screensOffBefore22 == true) {
        streak++
        date = date.minus(DatePeriod(days = 1))
    }
    return streak
}

/** Dagene i uka som starter på [monday], mandag først. */
fun weekDays(monday: LocalDate): List<LocalDate> = (0 until 7).map { monday.plusDays(it) }

val LocalDate.isWeekend: Boolean get() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
