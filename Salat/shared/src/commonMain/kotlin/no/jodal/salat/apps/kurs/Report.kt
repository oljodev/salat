package no.jodal.salat.apps.kurs

import kotlin.math.roundToInt
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** Rapport for én uke. Poengene er summen av dagspoeng, uten ukesbonus. */
data class WeekReport(
    val monday: LocalDate,
    val totalPoints: Int,
    val changePercent: Int?,
    val avgSleep: Double?,
    val avgFeeling: Double?,
    val screensOffDays: Int,
    val loggedDays: Int,
    val approvedDays: Int,
    val wins: List<Pair<LocalDate, String>>,
)

/** Samler uka fra [monday] og sammenligner poengene med forrige uke. */
fun weekReport(logs: List<DayLog>, monday: LocalDate): WeekReport {
    val days = logs.associateBy { it.date }.values
    val week = days.filter { it.date >= monday && it.date < monday.plusDays(7) }.sortedBy { it.date }
    val previous = days.filter { it.date >= monday.plusDays(-7) && it.date < monday }.sumOf { it.points }
    val total = week.sumOf { it.points }
    return WeekReport(
        monday = monday,
        totalPoints = total,
        changePercent = if (previous == 0) null else ((total - previous) * 100.0 / previous).roundToInt(),
        avgSleep = week.mapNotNull { it.sleepScore }.takeIf { it.isNotEmpty() }?.average(),
        avgFeeling = week.mapNotNull { it.bodyFeeling }.takeIf { it.isNotEmpty() }?.average(),
        screensOffDays = week.count { it.screensOffBefore22 == true },
        loggedDays = week.size,
        approvedDays = week.count { it.isApproved },
        wins = week.filter { it.dailyWin.isNotBlank() }.map { it.date to it.dailyWin },
    )
}

/** Viser inneværende uke på søndag, ellers forrige uke. */
fun reportWeek(today: LocalDate): LocalDate =
    today.weekStart().plusDays(if (today.dayOfWeek == DayOfWeek.SUNDAY) 0 else -7)
