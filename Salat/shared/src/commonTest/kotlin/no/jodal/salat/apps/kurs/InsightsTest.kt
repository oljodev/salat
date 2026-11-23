package no.jodal.salat.apps.kurs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlinx.datetime.LocalDate

class InsightsTest {
    private val monday = LocalDate(2026, 11, 2)
    private fun approved(date: LocalDate) = DayLog(date, screensOffBefore22 = true,
        screenQuality = ScreenQuality.High, paperReflection = true, shipAndEarn = true)

    @Test
    fun fallbackAndTodayReminder() {
        assertTrue(insights(emptyList(), monday, monday).single().contains("0 poeng"))
        val today = insights(listOf(DayLog(monday)), monday, monday)
        assertTrue(today.contains("Husk å legge inn Sleep Score i morgen tidlig."))
        assertFalse(insights(listOf(DayLog(monday)), monday, monday.plusDays(1)).any { it.contains("morgen tidlig") })
        assertTrue(insights(listOf(approved(monday)), monday, monday).any { it.contains("Godkjent dag") })
    }

    @Test
    fun sleepComparisonNeedsFourScoresPerGroupAndFivePointDifference() {
        val date = monday.plusDays(7)
        val logs = (0 until 8).map {
            DayLog(monday.plusDays(it), screensOffBefore22 = it < 4, sleepScore = if (it < 4) 85 else 80)
        }
        assertTrue(insights(logs, date, date).first().contains("på 85, mot 80"))
        assertFalse(insights(logs.drop(1), date, date).any { it.contains("Dataene") })
        assertFalse(insights(logs.map { it.copy(sleepScore = 82) }, date, date).any { it.contains("Dataene") })
        assertFalse(insights(logs, date.plusDays(30), date.plusDays(30)).any { it.contains("Dataene") })
        assertFalse(insights(logs, monday.plusDays(3), date).any { it.contains("Dataene") })
    }

    @Test
    fun restDayAndShippingReminderRequireMatchingDays() {
        val rest = DayLog(monday, isTrainingDay = false, movedOrRested = true, screenQuality = ScreenQuality.High)
        assertTrue(insights(listOf(rest), monday, monday).first().contains("60 poeng"))
        val logs = (0 until 3).map { rest.copy(date = monday.plusDays(it)) }
        val date = monday.plusDays(2)
        assertTrue(insights(logs, date, date).any { it.contains("kommersielle steg") })
        assertFalse(insights(logs.drop(1), date, date).any { it.contains("kommersielle steg") })
        assertFalse(insights(logs + logs.first().copy(shipAndEarn = true), date, date).any { it.contains("kommersielle steg") })
    }

    @Test
    fun weekComparisonUsesAveragePerLoggedDay() {
        val logs = listOf(approved(monday.plusDays(-7)).copy(shipAndEarn = false)) +
            (0 until 4).map { approved(monday.plusDays(it)) }
        val text = insights(logs, monday.plusDays(3), monday.plusDays(3)).joinToString(" ")
        assertTrue(text.contains("4/5"))
        assertTrue(text.contains("uke-rekke: 0 uker"))
        assertTrue(text.contains("24 % over"))
    }

    @Test
    fun milestoneAndAnalogWeekUseSevenCalendarDays() {
        val logs = (0 until 7).map { approved(monday.plusDays(it)).copy(physicalReading = true, sleepScore = 90) }
        val result = insights(logs, monday.plusDays(6), monday.plusDays(6))
        assertTrue(result.any { it.contains("7 dager på rad") })
        assertTrue(result.any { it.contains("papirrefleksjon") })
        assertTrue(result.size <= 4)
        assertTrue(result.sumOf { text -> text.count { it == '.' || it == '!' || it == '?' } } <= 4)
    }
}
