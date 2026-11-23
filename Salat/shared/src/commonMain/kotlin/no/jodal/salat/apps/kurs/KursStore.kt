package no.jodal.salat.apps.kurs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import no.jodal.salat.NativeBridge
import no.jodal.salat.apps.brems.BremsData
import no.jodal.salat.apps.brems.BremsStore
import no.jodal.salat.apps.brems.goalMillis

/** Holder på loggene i Kurs og lagrer dem på telefonen. */
class KursStore private constructor(private val bridge: NativeBridge) {
    var data by mutableStateOf(load())
        private set

    fun update(transform: (KursData) -> KursData) {
        data = transform(data)
        bridge.saveText(StorageKey, json.encodeToString(KursData.serializer(), data))
        scheduleNotices()
    }

    /** Planlegger varslene for den neste uka på nytt, etter det som er logget. */
    fun scheduleNotices() {
        bridge.kursNotify(json.encodeToString(ListSerializer(KursNotice.serializer()), kursNotices(data, now())))
    }

    fun updateSettings(transform: (KursSettings) -> KursSettings) = update { it.copy(settings = transform(it.settings)) }

    fun log(date: LocalDate): DayLog? = data.logs.firstOrNull { it.date == date }

    /** Lagrer [log], og erstatter en tidligere logg for samme dag. */
    fun save(log: DayLog) {
        val now = nowMillis()
        val old = log(log.date)
        val saved = log.copy(createdAtMillis = old?.createdAtMillis ?: now, updatedAtMillis = now)
        update { data -> data.copy(logs = (data.logs.filter { it.date != log.date } + saved).sortedBy { it.date }) }
    }

    /** En ny logg for [date], med det Brems vet om app-sperrene fylt ut. */
    fun prefill(date: LocalDate): DayLog {
        val kept = bremsKeptLimits(BremsStore.get(bridge).data, date, nowMillis(), TimeZone.currentSystemDefault())
        return DayLog(date = date, keptAppLimits = kept, keptAppLimitsFromBrems = kept != null)
    }

    fun delete(date: LocalDate) = update { data -> data.copy(logs = data.logs.filter { it.date != date }) }

    fun summary(): KursSummary = summarize(data.logs, today())

    fun exportJson(): String = prettyJson.encodeToString(KursData.serializer(), data)

    /** Erstatter alt med [text] fra [exportJson]. Gir en feilmelding, eller `null` hvis det gikk. */
    fun importJson(text: String): String? {
        if ("\"logs\"" !in text) return "Fant ingen Kurs-data i teksten."
        val imported = runCatching { json.decodeFromString(KursData.serializer(), text.trim()) }
            .getOrElse { return "Fant ingen Kurs-data i teksten." }
        update { imported }
        return null
    }

    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    fun now(): LocalDateTime = Instant.fromEpochMilliseconds(nowMillis()).toLocalDateTime(TimeZone.currentSystemDefault())

    fun today(): LocalDate = now().date

    private fun load(): KursData = bridge.loadText(StorageKey)
        ?.let { runCatching { json.decodeFromString(KursData.serializer(), it) }.getOrNull() }
        ?: KursData()

    companion object {
        private const val StorageKey = "kurs.data"
        private val json = Json { ignoreUnknownKeys = true }
        private val prettyJson = Json { prettyPrint = true; encodeDefaults = false }
        private var instance: KursStore? = null

        fun get(bridge: NativeBridge): KursStore = instance ?: KursStore(bridge).also { instance = it }
    }
}

/** Fra dette klokkeslettet logges i dag, før det gårsdagen hvis den mangler. */
const val SwitchToTodayHour = 14

/** Dagen check-in skal åpne: gårsdagen før kl. 14 hvis den ikke er logget, ellers i dag. */
fun defaultLogDate(now: LocalDateTime, logged: Set<LocalDate>): LocalDate {
    val today = now.date
    val yesterday = today.minus(DatePeriod(days = 1))
    return if (now.hour < SwitchToTodayHour && yesterday !in logged) yesterday else today
}

/**
 * Om Brems-målene ble holdt på [date]: `false` hvis et mål ble brutt, `true` hvis alle ble holdt, og `null` hvis
 * Brems ikke har mål for dagen. I dag gir bare `false`, siden dagen ikke er over.
 */
fun bremsKeptLimits(brems: BremsData, date: LocalDate, now: Long, zone: TimeZone): Boolean? {
    val goals = brems.goals.filter { it.since <= date }
    if (goals.isEmpty()) return null
    val broken = goals.any { brems.goalMillis(it, date, now, zone) >= it.minutesPerDay * 60_000L }
    val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
    return when {
        broken -> false
        date < today -> true
        else -> null
    }
}

/** Kalles fra Swift hver gang Salat åpnes, så varslene alltid gjelder den neste uka. */
fun kursSalatOpened(bridge: NativeBridge) = KursStore.get(bridge).scheduleNotices()
