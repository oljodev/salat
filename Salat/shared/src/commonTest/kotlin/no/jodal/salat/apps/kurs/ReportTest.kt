package no.jodal.salat.apps.kurs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class ReportTest {
    private val monday = LocalDate(2026, 11, 2)

    @Test
    fun emptyWeekHasNoAveragesOrChange() {
        val report = weekReport(emptyList(), monday)
        assertEquals(0, report.totalPoints)
        assertEquals(0, report.loggedDays)
        assertNull(report.avgSleep)
        assertNull(report.avgFeeling)
        assertNull(report.changePercent)
    }

    @Test
    fun reportUsesCalendarWeekAndLoggedValues() {
        val logs = listOf(
            DayLog(monday.plusDays(-1), paperReflection = true),
            DayLog(monday.plusDays(6), sleepScore = 90, dailyWin = "Leste", bodyFeeling = 4),
            DayLog(monday, screensOffBefore22 = true, dailyWin = "Gikk tur"),
            DayLog(monday.plusDays(1), dailyWin = "  "),
            DayLog(monday.plusDays(7), screensOffBefore22 = true),
        )
        val report = weekReport(logs, monday)
        assertEquals(65, report.totalPoints)
        assertEquals(160, report.changePercent)
        assertEquals(90.0, report.avgSleep)
        assertEquals(4.0, report.avgFeeling)
        assertEquals(1, report.screensOffDays)
        assertEquals(3, report.loggedDays)
        assertEquals(0, report.approvedDays)
        assertEquals(listOf(monday to "Gikk tur", monday.plusDays(6) to "Leste"), report.wins)
        assertEquals(-100, weekReport(logs.take(1), monday).changePercent)
    }

    @Test
    fun reportWeekChangesOnSunday() {
        for (offset in 0 until 6) assertEquals(monday.plusDays(-7), reportWeek(monday.plusDays(offset)))
        assertEquals(monday, reportWeek(monday.plusDays(6)))
        assertEquals(monday, reportWeek(monday.plusDays(7)))
    }
}
