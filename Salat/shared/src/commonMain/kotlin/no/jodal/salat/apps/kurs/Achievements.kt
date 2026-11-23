package no.jodal.salat.apps.kurs

import kotlinx.datetime.LocalDate

/** Merker som tjenes opp gjennom loggene. */
enum class Achievement(val title: String, val description: String) {
    AnalogArchitect("Analog Arkitekt", "10 dager med både papirrefleksjon og fysisk lesing."),
    Discipline22("22:00-Disiplin", "5 dager på rad med skjermen av før 22:00."),
    SleepScientist("Søvnforskeren", "7 dager på rad med Sleep Score og et snitt over 82."),
    Shipper("Shipper", "10 dager med kommersielle steg."),
    IronWeek("Jernuke", "7 av 7 godkjente dager i samme uke."),
}

/** Første opptjeningsdato og beste fremdrift, begrenset til målet. */
data class AchievementStatus(
    val achievement: Achievement,
    val unlockedOn: LocalDate?,
    val progress: Int,
    val goal: Int,
)

/** Regner ut merkene på nytt. Siste logg for samme dato gjelder. */
fun achievements(logs: List<DayLog>): List<AchievementStatus> {
    val days = logs.associateBy { it.date }.values.sortedBy { it.date }
    val byDate = days.associateBy { it.date }
    return Achievement.entries.map { achievement ->
        val goal = when (achievement) {
            Achievement.AnalogArchitect, Achievement.Shipper -> 10
            Achievement.Discipline22 -> 5
            else -> 7
        }
        var progress = 0
        var unlocked: LocalDate? = null
        var count = 0
        var previous: LocalDate? = null
        val approvedByWeek = mutableMapOf<LocalDate, Int>()
        for (day in days) {
            var reached = false
            val value = when (achievement) {
                Achievement.AnalogArchitect -> {
                    if (day.paperReflection == true && day.physicalReading == true) count++
                    count
                }
                Achievement.Shipper -> {
                    if (day.shipAndEarn == true) count++
                    count
                }
                Achievement.Discipline22 -> {
                    count = if (day.screensOffBefore22 == true) {
                        if (previous?.plusDays(1) == day.date) count + 1 else 1
                    } else 0
                    count
                }
                Achievement.SleepScientist -> {
                    val scores = (0 until 7).mapNotNull { byDate[day.date.plusDays(-it)]?.sleepScore }
                    reached = scores.size == 7 && scores.average() > 82
                    scores.size
                }
                Achievement.IronWeek -> {
                    val monday = day.date.weekStart()
                    val approved = (approvedByWeek[monday] ?: 0) + if (day.isApproved) 1 else 0
                    approvedByWeek[monday] = approved
                    approved
                }
            }
            progress = maxOf(progress, value).coerceAtMost(goal)
            if (achievement != Achievement.SleepScientist) reached = value >= goal
            if (reached && unlocked == null) unlocked = day.date
            previous = day.date
        }
        AchievementStatus(achievement, unlocked, progress, goal)
    }
}
