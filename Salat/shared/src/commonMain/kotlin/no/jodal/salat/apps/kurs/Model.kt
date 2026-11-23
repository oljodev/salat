package no.jodal.salat.apps.kurs

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Det Kurs husker. Poeng, rekker, freezes og achievements regnes ut fra [logs] og lagres ikke. */
@Serializable
data class KursData(
    val logs: List<DayLog> = emptyList(),
    val settings: KursSettings = KursSettings(),
)

@Serializable
data class KursSettings(
    val eveningReminder: Boolean = true,
    val morningReminder: Boolean = true,
    val afternoonReminder: Boolean = false,
)

/** Hvordan skjermtiden var. */
@Serializable
enum class ScreenQuality { High, Mixed, Low }

/** Loggen for én dag. Et felt som er `null`, er ikke svart på. */
@Serializable
data class DayLog(
    val date: LocalDate,
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
    /** Garmin Sleep Score for natten etter [date], 0–100. Fylles ofte inn morgenen etter. */
    val sleepScore: Int? = null,
    val screensOffBefore22: Boolean? = null,
    val paperReflection: Boolean? = null,
    val physicalReading: Boolean? = null,
    val screenQuality: ScreenQuality? = null,
    val keptAppLimits: Boolean? = null,
    /** Om [keptAppLimits] ble fylt ut av Brems og ikke av brukeren. */
    val keptAppLimitsFromBrems: Boolean = false,
    val isTrainingDay: Boolean? = null,
    /** Gåtur på en treningsfri dag, eller fullført økt og mikropauser på en treningsdag. */
    val movedOrRested: Boolean? = null,
    val shipAndEarn: Boolean? = null,
    val focusTag: FocusTag? = null,
    /** 1 = helt utladet, 5 = skarp og full av energi. */
    val bodyFeeling: Int? = null,
    /** Dagens viktigste seier eller innsikt, maks [MaxWinLength] tegn. */
    val dailyWin: String = "",
)

@Serializable
enum class FocusTag(val label: String) {
    Code("AI-Lab / Kode"),
    Book("Bok / Salg"),
    App("App / Verktøy"),
    Math("Matte / Læring"),
}

const val MaxWinLength = 140
