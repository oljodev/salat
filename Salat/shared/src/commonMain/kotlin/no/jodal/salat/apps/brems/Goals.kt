package no.jodal.salat.apps.brems

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/** Et mål om å bruke under [minutesPerDay] minutter om dagen på [app], eller på alle appene når [app] er tom. */
@Serializable
data class Goal(
    val id: String,
    val app: String,
    val minutesPerDay: Int,
    /** Dagen målet ble laget. Dager før det teller ikke. */
    val since: LocalDate,
    /** Når målet er nådd, er appen sperret resten av dagen, i stedet for bare en lang pause. */
    val hard: Boolean = false,
)

/** Hvordan det gikk med et mål én dag. */
enum class DayResult {
    /** Under målet. */
    Met,

    /** Over målet. */
    Missed,

    /** I dag, og fortsatt under målet. */
    OnTrack,

    /** Før målet ble laget. */
    Before,
}

/** Status for et mål: i dag, rekka av dager på rad og den siste uka. */
data class GoalStatus(
    val goal: Goal,
    val usedTodayMillis: Long,
    /** Dager på rad under målet, med i dag hvis man fortsatt er under. */
    val streak: Int,
    val bestStreak: Int,
    /** De siste sju dagene, i dag til slutt. */
    val week: List<Pair<LocalDate, DayResult>>,
) {
    val limitMillis: Long get() = goal.minutesPerDay * 60_000L
    val reached: Boolean get() = usedTodayMillis >= limitMillis
}

/** Om [goal] gjelder [app]. Et mål uten app gjelder alle. */
fun Goal.appliesTo(app: String): Boolean = this.app.isEmpty() || this.app.equals(app.trim(), ignoreCase = true)

/** «Snapchat», eller «alle apper». */
val Goal.label: String get() = app.ifEmpty { "Alle apper" }

/** Tiden som teller mot [goal] på [date]. */
fun BremsData.goalMillis(goal: Goal, date: LocalDate, now: Long, zone: TimeZone): Long =
    usageOn(date, now, zone).apps.filter { goal.appliesTo(it.first) }.sumOf { it.second }

fun BremsData.goalStatus(goal: Goal, now: Long, zone: TimeZone): GoalStatus {
    val today = today(now, zone)
    val limit = goal.minutesPerDay * 60_000L
    val usedToday = goalMillis(goal, today, now, zone)
    fun result(date: LocalDate): DayResult = when {
        date < goal.since -> DayResult.Before
        date == today -> if (usedToday < limit) DayResult.OnTrack else DayResult.Missed
        goalMillis(goal, date, now, zone) < limit -> DayResult.Met
        else -> DayResult.Missed
    }

    // Rekka: fra i dag og bakover, så lenge dagene er under målet. Går man over i dag, er den brutt.
    var streak = 0
    var day = today
    while (day >= goal.since && result(day) != DayResult.Missed) {
        streak++
        day = day.minus(DatePeriod(days = 1))
    }
    // Den lengste rekka siden målet ble laget, tilbake til det Brems husker.
    var best = 0
    var run = 0
    var d = maxOf(goal.since, today.minus(DatePeriod(days = (BremsRules.KeepMillis / 86_400_000L).toInt())))
    while (d <= today) {
        if (result(d) == DayResult.Missed) run = 0 else run++
        best = maxOf(best, run)
        d = d.plus(DatePeriod(days = 1))
    }
    val week = (6 downTo 0).map { today.minus(DatePeriod(days = it)) }.map { it to result(it) }
    return GoalStatus(goal, usedToday, streak, maxOf(best, streak), week)
}

/** Det første målet for [app] som er nådd i dag, hvis noen. */
fun BremsData.reachedGoal(app: String, now: Long, zone: TimeZone): Goal? =
    goals.filter { it.appliesTo(app) }.firstOrNull { goalMillis(it, today(now, zone), now, zone) >= it.minutesPerDay * 60_000L }

