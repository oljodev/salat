package no.jodal.salat.apps.kurs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class WeeksTest {
    /** Mandag 2. november 2026. */
    private val monday = LocalDate(2026, 11, 2)

    private fun full(date: LocalDate) = DayLog(
        date = date,
        sleepScore = 90,
        screensOffBefore22 = true,
        paperReflection = true,
        physicalReading = true,
        screenQuality = ScreenQuality.High,
        keptAppLimits = true,
        movedOrRested = true,
        shipAndEarn = true,
    )

    /** 45 + 35 + 25 + 15 = 120 poeng, akkurat godkjent. */
    private fun approved(date: LocalDate) = DayLog(
        date = date,
        screensOffBefore22 = true,
        screenQuality = ScreenQuality.High,
        movedOrRested = true,
        sleepScore = 80,
    )

    @Test
    fun pointsAddUp() {
        assertEquals(220, MaxDayPoints)
        assertEquals(220, full(monday).points)
        assertEquals(115, approved(monday).points)
        assertEquals(0, DayLog(monday).points)
        assertEquals(15, DayLog(monday, screenQuality = ScreenQuality.Mixed).points)
    }

    @Test
    fun sleepBonusLevels() {
        assertEquals(20, sleepBonus(85))
        assertEquals(10, sleepBonus(84))
        assertEquals(10, sleepBonus(75))
        assertEquals(0, sleepBonus(74))
        assertEquals(0, sleepBonus(null))
    }

    @Test
    fun weekStartsOnMonday() {
        assertEquals(monday, LocalDate(2026, 11, 8).weekStart())
        assertEquals(monday, monday.weekStart())
    }

    @Test
    fun weeklyStreakCountsFinishedWeeksAndCurrentIfReached() {
        val logs = (0 until 5).map { approved(monday.plusDays(it)) } +
            (7 until 12).map { approved(monday.plusDays(it)) } +
            (14 until 19).map { approved(monday.plusDays(it)) }
        val summary = summarize(logs, monday.plusDays(18))
        assertEquals(3, summary.weeklyStreak)
        assertEquals(2, summarize(logs.filter { it.date <= monday.plusDays(15) }, monday.plusDays(15)).weeklyStreak)
    }

    @Test
    fun perfectWeekGivesBonus() {
        val logs = (0 until 7).map { full(monday.plusDays(it)) }
        val summary = summarize(logs, monday.plusDays(6))
        assertEquals(7 * 220 + 150, summary.totalPoints)
        assertEquals(true, summary.currentWeek!!.perfect)
    }

    @Test
    fun freezeSavesAWeekOfFour() {
        // Uke 1: 7 fulle dager gir 1690 poeng og én freeze. Uke 2: bare 4 godkjente.
        val logs = (0 until 7).map { full(monday.plusDays(it)) } + (7 until 11).map { approved(monday.plusDays(it)) }
        val summary = summarize(logs, monday.plusDays(14))
        assertEquals(true, summary.weeks[1].frozen)
        assertEquals(2, summary.weeklyStreak)
        // Uke 2 ga en ny freeze (over 2000 poeng), og én ble brukt.
        assertEquals(1, summary.availableFreezes)
    }

    @Test
    fun noFreezeMeansTheStreakBreaks() {
        val logs = (0 until 5).map { approved(monday.plusDays(it)) } + (7 until 10).map { approved(monday.plusDays(it)) }
        assertEquals(0, summarize(logs, monday.plusDays(14)).weeklyStreak)
    }

    @Test
    fun freezesAreCappedAtTwo() {
        val logs = (0 until 21).map { full(monday.plusDays(it)) }
        assertEquals(2, summarize(logs, monday.plusDays(20)).availableFreezes)
    }

    @Test
    fun screensOffStreakToleratesUnloggedTodayAndYesterday() {
        val logs = (0 until 5).map { DayLog(monday.plusDays(it), screensOffBefore22 = true) }
        assertEquals(5, summarize(logs, monday.plusDays(4)).daily22Streak)
        assertEquals(5, summarize(logs, monday.plusDays(6)).daily22Streak)
        assertEquals(0, summarize(logs, monday.plusDays(7)).daily22Streak)
        val broken = logs + DayLog(monday.plusDays(5), screensOffBefore22 = false)
        assertEquals(0, summarize(broken, monday.plusDays(5)).daily22Streak)
    }

    @Test
    fun defaultDateIsYesterdayInTheMorningIfNotLogged() {
        val tuesday = monday.plusDays(1)
        fun at(hour: Int) = LocalDateTime(tuesday.year, tuesday.month, tuesday.day, hour, 0)
        assertEquals(monday, defaultLogDate(at(8), emptySet()))
        assertEquals(tuesday, defaultLogDate(at(8), setOf(monday)))
        assertEquals(tuesday, defaultLogDate(at(14), emptySet()))
        assertEquals(monday, defaultLogDate(at(0), emptySet()))
    }
}
