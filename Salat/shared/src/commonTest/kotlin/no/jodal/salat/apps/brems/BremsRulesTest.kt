package no.jodal.salat.apps.brems

import kotlin.random.Random
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BremsRulesTest {
    private val sec = 1000L
    private val min = 60 * sec
    private val start = 1_000_000_000L

    private fun open(data: BremsData, app: String, at: Long) = BremsRules.opened(data, app, at, TimeZone.UTC, Random(1))

    @Test
    fun firstOpenGivesTheShortPause() {
        val (data, decision) = open(BremsData(), "TikTok", start)
        val pause = assertIs<Decision.Wait>(decision).pause
        assertEquals(PauseKind.Open, pause.kind)
        assertEquals(start + 10 * sec, pause.untilMillis)
        assertNull(data.active)
    }

    @Test
    fun reopeningDuringThePauseShowsTheSamePause() {
        val (data, first) = open(BremsData(), "TikTok", start)
        val (_, second) = open(data, "YouTube", start + 4 * sec)
        assertEquals(assertIs<Decision.Wait>(first).pause, assertIs<Decision.Wait>(second).pause)
    }

    @Test
    fun continuingLetsYouBackInWithoutANewPause() {
        val (paused, _) = open(BremsData(), "TikTok", start)
        val continued = BremsRules.continueToApp(paused, start + 11 * sec)
        val (data, decision) = open(continued, "TikTok", start + 12 * sec)
        assertEquals(Decision.Go, decision)
        assertEquals(ActiveSession("TikTok", start + 12 * sec), data.active)
        assertEquals(PauseOutcome.Continued, data.pauses.last().outcome)
    }

    @Test
    fun goingStraightBackAfterThePauseCountsAsContinuing() {
        val (paused, _) = open(BremsData(), "TikTok", start)
        val (data, decision) = open(paused, "TikTok", start + 20 * sec)
        assertEquals(Decision.Go, decision)
        assertEquals(PauseOutcome.Continued, data.pauses.last().outcome)
    }

    @Test
    fun comingBackMuchLaterCountsAsStoppingAndGivesANewPause() {
        val (paused, _) = open(BremsData(), "TikTok", start)
        val (data, decision) = open(paused, "TikTok", start + 30 * min)
        assertIs<Decision.Wait>(decision)
        assertEquals(PauseOutcome.Stopped, data.pauses[0].outcome)
        assertEquals(2, data.pauses.size)
    }

    @Test
    fun aLongSessionGivesTheLongPause() {
        val inApp = BremsData(active = ActiveSession("TikTok", start))
        val closed = BremsRules.closed(inApp, start + 12 * min)
        val (_, decision) = open(closed, "TikTok", start + 12 * min + 5 * sec)
        val pause = assertIs<Decision.Wait>(decision).pause
        assertEquals(PauseKind.Limit, pause.kind)
        assertEquals(55, ((pause.untilMillis - pause.startMillis) / sec).toInt())
    }

    @Test
    fun timeAwayCountsTowardsTheLongPause() {
        val inApp = BremsData(active = ActiveSession("TikTok", start))
        val closed = BremsRules.closed(inApp, start + 12 * min)
        val (_, decision) = open(closed, "TikTok", start + 12 * min + 2 * min)
        assertEquals(PauseKind.Open, assertIs<Decision.Wait>(decision).pause.kind)
    }

    @Test
    fun aShortSessionGivesTheShortPause() {
        val inApp = BremsData(active = ActiveSession("TikTok", start))
        val closed = BremsRules.closed(inApp, start + 4 * min)
        val (_, decision) = open(closed, "TikTok", start + 4 * min + 5 * sec)
        assertEquals(PauseKind.Open, assertIs<Decision.Wait>(decision).pause.kind)
    }

    @Test
    fun aCloseRightAfterAnOpenBelongsToThePreviousApp() {
        val inApp = BremsData(active = ActiveSession("YouTube", start))
        assertEquals(inApp, BremsRules.closed(inApp, start + 1 * sec))
    }

    @Test
    fun aSessionThatNeverClosedIsCapped() {
        val forgotten = BremsData(active = ActiveSession("TikTok", start))
        val (data, _) = open(forgotten, "TikTok", start + 5 * 60 * min)
        assertEquals(BremsRules.MaxSessionMillis, data.sessions.single().millis)
    }

    @Test
    fun noPauseWhenTurnedOff() {
        val off = BremsData(settings = BremsSettings(enabled = false))
        assertEquals(Decision.Go, open(off, "TikTok", start).second)
    }

    @Test
    fun noShortPauseWhenSetToZero() {
        val data = BremsData(settings = BremsSettings(openPauseSeconds = 0))
        assertEquals(Decision.Go, open(data, "TikTok", start).second)
    }

    @Test
    fun notificationsComeAtTheReminderTheLimitAndAfter() {
        val notices = BremsRules.notifications(BremsSettings(nagMinutes = 2), "TikTok")
        assertEquals(listOf(5, 10, 12, 15, 20, 25), notices.map { it.minutes })
        assertEquals("5 minutter i TikTok", notices.first().title)
    }

    @Test
    fun noNagsWhenTurnedOff() {
        val notices = BremsRules.notifications(BremsSettings(nagMinutes = 0), "TikTok")
        assertEquals(listOf(5, 10), notices.map { it.minutes })
    }

    @Test
    fun formatsSeconds() {
        assertEquals("45 sekunder", formatSeconds(45))
        assertEquals("1 minutt", formatSeconds(60))
        assertEquals("1 min 30 sek", formatSeconds(90))
    }
}
