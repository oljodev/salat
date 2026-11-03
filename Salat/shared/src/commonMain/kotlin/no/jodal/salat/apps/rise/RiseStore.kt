package no.jodal.salat.apps.rise

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import no.jodal.salat.NativeBridge

/** Holder på alarmene i Rise, lagrer dem og sørger for at iOS har planlagt dem. */
class RiseStore(
    private val bridge: NativeBridge,
    private val currentTime: () -> LocalDateTime = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) },
) {
    var data by mutableStateOf(load())
        private set

    /** Feilmelding fra siste gang alarmene ble planlagt, eller `null`. */
    var scheduleError by mutableStateOf<String?>(null)
        private set

    fun update(transform: (RiseData) -> RiseData) = updateAndSchedule(transform) {}

    /** Returns the scheduling result for this particular update, including asynchronous iOS errors. */
    suspend fun updateAndAwait(transform: (RiseData) -> RiseData): String? =
        suspendCancellableCoroutine { continuation ->
            updateAndSchedule(transform) { error ->
                if (continuation.isActive) continuation.resume(error)
            }
        }

    private fun updateAndSchedule(transform: (RiseData) -> RiseData, onResult: (String?) -> Unit) {
        val now = now()
        val zone = TimeZone.currentSystemDefault()
        data = transform(data).let { updated ->
            updated.copy(
                alarms = updated.alarms
                    // Engangsalarmer som har ringt, slettes.
                    .filterNot { it.isDone(now, zone) }
                    // Hoppede dager som har vært, trengs ikke lenger.
                    .map { a -> a.copy(skippedDates = a.skippedDates.filter { it >= now.date }.toSet()) },
            )
        }
        bridge.saveText(StorageKey, json.encodeToString(RiseData.serializer(), data))
        reschedule(onResult)
    }

    /** Kalles når appen åpnes, for å fylle på alarmer og rydde bort engangsalarmer som har ringt. */
    fun refresh() {
        val now = now()
        if (data.alarms.any { it.isDone(now, TimeZone.currentSystemDefault()) }) update { it } else reschedule()
    }

    fun now(): LocalDateTime = currentTime()

    private fun reschedule(onResult: (String?) -> Unit = {}) {
        val plan = RisePlan(
            items = RisePlanner.plan(data, now(), TimeZone.currentSystemDefault()),
            activeAlarmIds = data.alarms.filter { it.enabled }.map { it.id },
        )
        bridge.applyRiseSchedule(json.encodeToString(RisePlan.serializer(), plan)) { error ->
            scheduleError = error
            onResult(error)
        }
    }

    private fun load(): RiseData = bridge.loadText(StorageKey)
        ?.let { runCatching { json.decodeFromString(RiseData.serializer(), it) }.getOrNull() }
        ?: RiseData()

    companion object {
        private const val StorageKey = "rise.data"
        private val json = Json { ignoreUnknownKeys = true }
        private var instance: RiseStore? = null

        fun get(bridge: NativeBridge): RiseStore = instance ?: RiseStore(bridge).also { instance = it }
    }
}

/** Kalles fra Swift hver gang appen åpnes. */
fun refreshRise(bridge: NativeBridge) = RiseStore.get(bridge).refresh()
