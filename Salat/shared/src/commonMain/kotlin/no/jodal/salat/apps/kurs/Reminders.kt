package no.jodal.salat.apps.kurs

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/** Et varsel fra Kurs på et klokkeslett. Trykker man på det, åpnes [link]. */
@Serializable
data class KursNotice(
    val title: String,
    val body: String,
    /** Når varselet skal komme, som `2026-11-23T21:45`, i telefonens tidssone. */
    val at: String,
    val link: String = LogLink,
)

/** Lenken som åpner check-in. */
const val LogLink = "salat://kurs/logg"

/** Så mange dager fram planlegges varslene. Kurs planlegger på nytt hver gang Salat åpnes eller noe logges. */
const val ReminderDays = 7

/** Varslene for de neste [ReminderDays] dagene etter [now], etter innstillingene og det som er logget. */
fun kursNotices(data: KursData, now: LocalDateTime): List<KursNotice> {
    val logs = data.logs.associateBy { it.date }
    val settings = data.settings
    val notices = mutableListOf<KursNotice>()
    for (offset in 0 until ReminderDays) {
        val date = now.date.plusDays(offset)
        if (settings.morningReminder && logs[date.plusDays(-1)] == null) {
            val time = if (date.isWeekend) LocalTime(9, 30) else LocalTime(8, 15)
            notices += KursNotice(
                "Klar for 45 sekunders logg?",
                "Legg inn nattens Garmin Sleep Score og gårsdagens seier.",
                LocalDateTime(date, time).toString(),
            )
        }
        if (settings.afternoonReminder && logs[date]?.isTrainingDay != true) {
            notices += KursNotice(
                "Tid for en luftetur",
                "15 minutter ute, eller 2 minutter tøying og armhevinger vekk fra PC-en.",
                LocalDateTime(date, LocalTime(16, 30)).toString(),
                link = "salat://kurs",
            )
        }
        if (settings.eveningReminder && logs[date]?.screensOffBefore22 == null) {
            notices += KursNotice(
                "15 min til skjermstopp",
                "Kl. 22:00 skal skjermene av. Lagre koden, finn fram notatboka og gjør klar for analog kveld.",
                LocalDateTime(date, LocalTime(21, 45)).toString(),
                link = "salat://kurs",
            )
        }
    }
    return notices.filter { LocalDateTime.parse(it.at) > now }
}
