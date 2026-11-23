package no.jodal.salat.apps.kurs

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate

/** Gir korte råd etter lagring, med historikk til og med [date]. Klokkeslett brukes ikke. */
fun insights(logs: List<DayLog>, date: LocalDate, today: LocalDate): List<String> {
    val history = logs.associateBy { it.date }.values.filter { it.date <= date }
    val byDate = history.associateBy { it.date }
    val day = byDate[date]
    val last30 = history.filter { it.date >= date.plusDays(-29) }
    val last7 = history.filter { it.date >= date.plusDays(-6) }
    val summary = summarize(history, date)
    val candidates = mutableListOf<String>()
    val early = last30.filter { it.screensOffBefore22 == true }.mapNotNull { it.sleepScore }
    val late = last30.filter { it.screensOffBefore22 == false }.mapNotNull { it.sleepScore }
    if (early.size >= 4 && late.size >= 4 && early.average() - late.average() >= 5) {
        candidates += "Dataene dine er tydelige: Når du skrur av skjermen før 22:00, ligger Garmin Sleep Score i snitt på ${early.average().roundToInt()}, mot ${late.average().roundToInt()} når skjermen er på lenger."
    }
    if (day?.isTrainingDay == false && day.movedOrRested == true && day.screenQuality == ScreenQuality.High) {
        candidates += "Sterk treningsfri dag! Lufteturen mellom øktene ga uttelling – du traff høy kvalitetsskjermtid og sikret ${day.points} poeng."
    }
    val approved = summary.currentWeek?.approvedDays ?: 0
    if (approved >= 4) {
        var status = "Du er nå på $approved/5 godkjente dager denne uka (uke-rekke: ${summary.weeklyStreak} uker)."
        val monday = date.weekStart()
        val previous = history.filter { it.date >= monday.plusDays(-7) && it.date < monday }
        val current = history.filter { it.date >= monday }
        val previousAverage = previous.map { it.points }.takeIf { it.isNotEmpty() }?.average()
        if (previousAverage != null && previousAverage > 0) {
            val change = ((current.map { it.points }.average() - previousAverage) * 100 / previousAverage).roundToInt()
            status += " Snittpoengene dine ligger ${abs(change)} % ${if (change < 0) "under" else "over"} forrige uke."
        }
        candidates += status
    }
    val three = (0 until 3).map { byDate[date.plusDays(-it)] }
    if (three.all { it?.screenQuality == ScreenQuality.High && it.shipAndEarn != true }) {
        candidates += "Mye bra bygging de siste 3 dagene, men null kommersielle steg. Hva er én liten ting du kan gjøre i morgen for å få et prosjekt ut til folk?"
    }
    if (day?.screensOffBefore22 == true && summary.daily22Streak in listOf(5, 7, 14, 30)) {
        candidates += "Du har skrudd av skjermen før 22:00 i ${summary.daily22Streak} dager på rad."
    }
    if (last7.size == 7 && last7.all { it.paperReflection == true && it.physicalReading == true }) {
        candidates += "Du har gitt plass til både papirrefleksjon og fysisk lesing alle de siste 7 dagene."
    }
    if (day != null && date == today && day.sleepScore == null) {
        candidates += "Husk å legge inn Sleep Score i morgen tidlig."
    }
    val points = day?.points ?: 0
    val fallback = if (day?.isApproved == true) {
        "Godkjent dag med $points poeng."
    } else {
        "$points poeng. ${Points.ApprovedDay - points} til, så hadde dagen vært godkjent."
    }
    if (candidates.size < 2) candidates += fallback
    var sentences = 0
    return candidates.filter { text ->
        val count = text.count { it == '.' || it == '!' || it == '?' }
        (sentences + count <= 4).also { if (it) sentences += count }
    }.take(4)
}
