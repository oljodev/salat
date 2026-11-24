package no.jodal.salat.apps.brems

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class BlocksTest {
    private val zone = TimeZone.UTC
    private val min = 60_000L

    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime(2026, 10, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    private fun open(data: BremsData, app: String, now: Long) = BremsRules.opened(data, app, now, zone, Random(1))

    private val hardGoal = Goal("g", "Snapchat", 15, LocalDate(2026, 10, 18), hard = true)
    private val used = listOf(Session("Snapchat", at(22, 12), at(22, 12) + 16 * min))

    @Test
    fun aReachedHardGoalBlocksTheAppUntilMidnight() {
        val (_, decision) = open(BremsData(goals = listOf(hardGoal), sessions = used), "Snapchat", at(22, 18))
        val pause = assertIs<Decision.Wait>(decision).pause
        assertEquals(PauseKind.Block, pause.kind)
        assertEquals(BlockReason.Goal, pause.blockReason)
        assertEquals(at(23, 0), pause.untilMillis)
    }

    @Test
    fun aHardGoalDoesNotBlockOtherApps() {
        val (_, decision) = open(BremsData(goals = listOf(hardGoal), sessions = used), "TikTok", at(22, 18))
        assertEquals(PauseKind.Open, assertIs<Decision.Wait>(decision).pause.kind)
    }

    @Test
    fun theBlockCannotBeWaitedOutOrContinuedPast() {
        val start = BremsData(goals = listOf(hardGoal), sessions = used)
        val (blocked, first) = open(start, "Snapchat", at(22, 18))
        // «Fortsett» gir et pass, men sperren går foran.
        val continued = BremsRules.continueToApp(blocked, at(22, 18, 1))
        val (again, second) = open(continued, "Snapchat", at(22, 19))
        assertEquals(PauseKind.Block, assertIs<Decision.Wait>(second).pause.kind)
        // Å gi opp og prøve igjen gir en ny sperre.
        val (_, third) = open(BremsRules.stop(again), "Snapchat", at(22, 20))
        assertEquals(PauseKind.Block, assertIs<Decision.Wait>(third).pause.kind)
        assertEquals(assertIs<Decision.Wait>(first).pause.untilMillis, assertIs<Decision.Wait>(third).pause.untilMillis)
    }

    @Test
    fun theNextDayTheAppIsFreeAgain() {
        val (blocked, _) = open(BremsData(goals = listOf(hardGoal), sessions = used), "Snapchat", at(22, 18))
        val (data, decision) = open(blocked, "Snapchat", at(23, 9))
        assertEquals(PauseKind.Open, assertIs<Decision.Wait>(decision).pause.kind)
        assertEquals(PauseOutcome.Stopped, data.pauses[0].outcome)
    }

    @Test
    fun bedtimeBlocksOverMidnight() {
        val settings = BremsSettings(bedtime = Bedtime(enabled = true, fromMinute = 22 * 60, toMinute = 7 * 60))
        assertEquals(at(23, 7), bedtimeEnd(settings.bedtime, at(22, 23), zone))
        assertEquals(at(23, 7), bedtimeEnd(settings.bedtime, at(23, 3), zone))
        assertNull(bedtimeEnd(settings.bedtime, at(23, 12), zone))
        val (_, decision) = open(BremsData(settings = settings), "TikTok", at(22, 23))
        assertEquals(BlockReason.Bedtime, assertIs<Decision.Wait>(decision).pause.blockReason)
    }

    @Test
    fun aBlockFromShortcutsBlocksEveryApp() {
        val data = BremsRules.block(BremsData(), 60, at(22, 12))
        val (_, decision) = open(data, "", at(22, 12, 30))
        val pause = assertIs<Decision.Wait>(decision).pause
        assertEquals(BlockReason.Manual, pause.blockReason)
        assertEquals(at(22, 13), pause.untilMillis)
        // En kortere sperre forkorter ikke den som gjelder.
        assertEquals(at(22, 13), BremsRules.block(data, 10, at(22, 12, 30)).blockUntilMillis)
    }

    @Test
    fun nothingIsBlockedWhenBremsIsOff() {
        val data = BremsRules.block(BremsData(settings = BremsSettings(enabled = false)), 60, at(22, 12))
        assertEquals(Decision.Go, open(data, "TikTok", at(22, 12, 30)).second)
    }
}

class OneAutomationTest {
    private val min = 60_000L
    private val start = 1_000_000_000L

    @Test
    fun eventsAlternateBetweenOpenedAndClosed() {
        assertEquals(true, BremsRules.isOpening(BremsData(), start, salatActive = false))
        val open = BremsData(foreground = true, lastEventMillis = start)
        assertEquals(false, BremsRules.isOpening(open, start + 5 * min, salatActive = false))
    }

    @Test
    fun anEventWhileSalatIsFrontIsTheAppThatWasLeft() {
        // Pausen åpnet Salat, og så sier Snarveier at appen ble lukket. Da må det ikke bli en ny pause.
        assertEquals(false, BremsRules.isOpening(BremsData(foreground = false), start, salatActive = true))
    }

    @Test
    fun aMissedCloseIsForgottenAfterAnHour() {
        val stale = BremsData(foreground = true, lastEventMillis = start)
        assertEquals(true, BremsRules.isOpening(stale, start + 61 * min, salatActive = false))
    }
}
