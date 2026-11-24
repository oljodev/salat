package no.jodal.salat.apps.brems

import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** En hard sperre: appen kan ikke brukes før [untilMillis]. [goal] er målet som ble nådd, når det er grunnen. */
data class Block(val reason: BlockReason, val untilMillis: Long, val goal: Goal? = null)

/**
 * Sperren som gjelder for [app] nå, hvis noen. Gjelder flere, vinner den som varer lengst.
 * En tom [app] betyr at vi ikke vet hvilken app det er; da gjelder bare mål for alle apper.
 */
fun BremsData.blockFor(app: String, now: Long, zone: TimeZone): Block? {
    if (!settings.enabled) return null
    val blocks = buildList {
        blockUntilMillis?.takeIf { it > now }?.let { add(Block(BlockReason.Manual, it)) }
        bedtimeEnd(settings.bedtime, now, zone)?.let { add(Block(BlockReason.Bedtime, it)) }
        goals.filter { it.hard && it.appliesTo(app) && goalMillis(it, today(now, zone), now, zone) >= it.minutesPerDay * 60_000L }
            .forEach { add(Block(BlockReason.Goal, startOfDay(today(now, zone).plus(DatePeriod(days = 1)), zone), it)) }
    }
    return blocks.maxByOrNull { it.untilMillis }
}

/** Om noen sperre gjelder nå, for noen app. Da kan ikke Brems eller sperrene skrus av. */
fun BremsData.anyBlock(now: Long, zone: TimeZone): Boolean =
    blockFor(UnnamedApp, now, zone) != null || goals.any { it.hard && hardGoalReached(it, now, zone) }

/** Om [goal] har hard grense og er nådd i dag, så den ikke kan endres. */
fun BremsData.hardGoalReached(goal: Goal, now: Long, zone: TimeZone): Boolean =
    settings.enabled && goal.hard && goalMillis(goal, today(now, zone), now, zone) >= goal.minutesPerDay * 60_000L

/** Når sengetida slutter, hvis den gjelder nå. */
fun bedtimeEnd(bedtime: Bedtime, now: Long, zone: TimeZone): Long? {
    if (!bedtime.enabled || bedtime.fromMinute == bedtime.toMinute) return null
    val local = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone)
    val minute = local.hour * 60 + local.minute
    val date = local.date
    val from = bedtime.fromMinute
    val to = bedtime.toMinute
    return when {
        from < to -> if (minute in from until to) at(date, to, zone) else null
        minute >= from -> at(date.plus(DatePeriod(days = 1)), to, zone)
        minute < to -> at(date, to, zone)
        else -> null
    }
}

/** Når sengetida begynner neste gang, etter [now]. */
fun bedtimeStart(bedtime: Bedtime, now: Long, zone: TimeZone): Long? {
    if (!bedtime.enabled || bedtime.fromMinute == bedtime.toMinute) return null
    val date = today(now, zone)
    val todayStart = at(date, bedtime.fromMinute, zone)
    return if (todayStart > now) todayStart else at(date.plus(DatePeriod(days = 1)), bedtime.fromMinute, zone)
}

private fun at(date: LocalDate, minute: Int, zone: TimeZone): Long =
    LocalDateTime(date, LocalTime(minute / 60, minute % 60)).toInstant(zone).toEpochMilliseconds()

private fun startOfDay(date: LocalDate, zone: TimeZone): Long = date.atStartOfDayIn(zone).toEpochMilliseconds()

/** «22:00». */
fun formatClock(minute: Int): String = "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

/** «kl. 07:00», eller «i morgen kl. 07:00» når det er en annen dag enn [now]. */
fun formatUntil(untilMillis: Long, now: Long, zone: TimeZone): String {
    val local = Instant.fromEpochMilliseconds(untilMillis).toLocalDateTime(zone)
    // Midnatt er slutten av i dag, ikke starten av i morgen.
    if (local.hour == 0 && local.minute == 0) return "midnatt"
    val clock = "kl. " + formatClock(local.hour * 60 + local.minute)
    return when (local.date) {
        today(now, zone) -> clock
        today(now, zone).plus(DatePeriod(days = 1)) -> "i morgen $clock"
        else -> "${local.date.day}. ${local.date.month.ordinal + 1}. $clock"
    }
}

/** Hva sperren gjelder, til pauseskjermen: «Sengetid til kl. 07:00.» */
fun Block.describe(app: String, now: Long, zone: TimeZone): String = describeBlock(reason, untilMillis, goal, app, now, zone)

fun describeBlock(reason: BlockReason?, untilMillis: Long, goal: Goal?, app: String, now: Long, zone: TimeZone): String {
    val until = formatUntil(untilMillis, now, zone)
    return when (reason) {
        BlockReason.Goal -> if (goal != null) {
            "Du har brukt opp ${formatMinutes(goal.minutesPerDay)} i ${if (goal.app.isEmpty()) "appene" else goal.app} i dag. Sperret til $until."
        } else {
            "Dagens tid er brukt opp. Sperret til $until."
        }
        BlockReason.Bedtime -> "Sengetid. ${appName(app).replaceFirstChar { it.uppercase() }} er sperret til $until."
        BlockReason.Manual, null -> "Sperret til $until."
    }
}
