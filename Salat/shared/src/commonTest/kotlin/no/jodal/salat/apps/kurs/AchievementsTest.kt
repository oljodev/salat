package no.jodal.salat.apps.kurs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class AchievementsTest {
    private val monday = LocalDate(2026, 11, 2)
    private fun status(logs: List<DayLog>, achievement: Achievement) =
        achievements(logs).single { it.achievement == achievement }

    @Test
    fun emptyHistoryHasNoBadges() {
        assertEquals(5, achievements(emptyList()).size)
        achievements(emptyList()).forEach {
            assertEquals(0, it.progress)
            assertNull(it.unlockedOn)
        }
    }

    @Test
    fun cumulativeBadgesCountDaysAndKeepFirstDate() {
        val logs = (0 until 12).map {
            DayLog(monday.plusDays(it * 2), paperReflection = true, physicalReading = true, shipAndEarn = true)
        }
        for (badge in listOf(Achievement.AnalogArchitect, Achievement.Shipper)) {
            val result = status(logs.reversed() + logs.first(), badge)
            assertEquals(10, result.progress)
            assertEquals(monday.plusDays(18), result.unlockedOn)
        }
        assertEquals(0, status(listOf(DayLog(monday, paperReflection = true)), Achievement.AnalogArchitect).progress)
    }

    @Test
    fun disciplineRequiresConsecutiveDates() {
        val logs = (0 until 4).map { DayLog(monday.plusDays(it), screensOffBefore22 = true) } +
            (5 until 11).map { DayLog(monday.plusDays(it), screensOffBefore22 = true) }
        assertEquals(4, status(logs.take(5), Achievement.Discipline22).progress)
        assertNull(status(logs.take(5), Achievement.Discipline22).unlockedOn)
        assertEquals(monday.plusDays(9), status(logs, Achievement.Discipline22).unlockedOn)
    }

    @Test
    fun sleepNeedsSevenScoresAndStrictlyMoreThan82() {
        val low = (0 until 7).map { DayLog(monday.plusDays(it), sleepScore = 82) }
        assertNull(status(low, Achievement.SleepScientist).unlockedOn)
        assertEquals(7, status(low, Achievement.SleepScientist).progress)
        val high = low + DayLog(monday.plusDays(7), sleepScore = 89)
        assertEquals(monday.plusDays(7), status(high, Achievement.SleepScientist).unlockedOn)
        val gap = high.filter { it.date != monday.plusDays(3) }
        assertNull(status(gap, Achievement.SleepScientist).unlockedOn)
        assertEquals(6, status(gap, Achievement.SleepScientist).progress)
    }

    @Test
    fun ironWeekUsesApprovedDaysInOneCalendarWeek() {
        val logs = (0 until 14).map {
            DayLog(monday.plusDays(it), screensOffBefore22 = true, screenQuality = ScreenQuality.High,
                paperReflection = true, physicalReading = true)
        }
        assertEquals(monday.plusDays(6), status(logs.reversed(), Achievement.IronWeek).unlockedOn)
        assertEquals(7, status(logs, Achievement.IronWeek).progress)
        assertNull(status(logs.filterIndexed { i, _ -> i != 3 && i != 10 }, Achievement.IronWeek).unlockedOn)
    }
}
