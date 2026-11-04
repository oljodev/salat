package no.jodal.salat.apps.brems

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import no.jodal.salat.NativeBridge

/** Holder på det Brems husker, og lagrer det på telefonen. Brukes både av skjermene og av Snarveier. */
class BremsStore private constructor(private val bridge: NativeBridge) {
    var data by mutableStateOf(load())
        private set

    fun update(transform: (BremsData) -> BremsData) {
        data = transform(data)
        bridge.saveText(StorageKey, json.encodeToString(BremsData.serializer(), data))
    }

    fun updateSettings(transform: (BremsSettings) -> BremsSettings) = update { it.copy(settings = transform(it.settings)) }

    /** En app er åpnet. Gir `true` hvis Brems skal vise en pause. */
    fun opened(app: String?): Boolean {
        val now = nowMillis()
        val before = data.active
        val (next, decision) = BremsRules.opened(BremsRules.trim(data, now), app, now)
        update { next }
        if (next.active != before) scheduleNotices()
        val name = appName(app.orEmpty()).replaceFirstChar { it.uppercase() }
        log(
            when (decision) {
                is Decision.Wait -> "$name åpnet: pause, ${(decision.pause.untilMillis - now + 999) / 1000} s igjen"
                Decision.Go -> "$name åpnet: fri"
            },
        )
        return decision is Decision.Wait
    }

    fun closed() {
        val before = data.active
        val now = nowMillis()
        update { BremsRules.closed(it, now) }
        val ended = before?.takeIf { data.active != it }
        if (ended != null) {
            scheduleNotices()
            log("${appName(ended.app).replaceFirstChar { it.uppercase() }} lukket etter ${formatDuration(now - ended.startMillis)}")
        } else {
            log("Lukket")
        }
    }

    /** Salat er åpnet, så appen som var åpen er forlatt. Avslutter økta og fjerner varslene. */
    fun salatOpened() {
        if (data.active != null) closed()
    }

    /** Skriver [text] i loggen. */
    fun log(text: String) = update { it.copy(log = (it.log + LogEntry(nowMillis(), text)).takeLast(LogSize)) }

    fun currentPause(): Pause? = BremsRules.currentPause(data, nowMillis())

    /** Går tilbake til appen etter pausen. Kjenner vi appen, åpnes den. */
    fun continueToApp() {
        val app = data.pauses.lastOrNull()?.app.orEmpty()
        update { BremsRules.continueToApp(it, nowMillis()) }
        appUrl(app)?.let(bridge::openUrl)
    }

    fun stop() = update(BremsRules::stop)

    fun answerWhy(why: String?) = update { BremsRules.answerWhy(it, why) }

    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    /** Planlegger varslene for økta som pågår, eller fjerner dem hvis ingen app er åpen. */
    private fun scheduleNotices() {
        val active = data.active
        val notices = if (active == null || !data.settings.enabled) {
            emptyList()
        } else {
            val now = nowMillis()
            val zone = TimeZone.currentSystemDefault()
            val session = BremsRules.notifications(data.settings, active.app).map {
                ScheduledNotice(it.title, it.body, active.startMillis + it.minutes * 60_000L)
            }
            // Et varsel når dagens mål for appen nås, hvis det skjer i denne økta.
            val goals = data.goals.filter { it.appliesTo(active.app) }.mapNotNull { goal ->
                val left = goal.minutesPerDay * 60_000L - data.goalMillis(goal, today(now, zone), now, zone)
                if (left <= 0) return@mapNotNull null
                ScheduledNotice(
                    if (goal.app.isEmpty()) "Dagens mål er nådd" else "Målet for ${goal.app} er nådd",
                    "Du har brukt ${formatMinutes(goal.minutesPerDay)} i dag. Lukk appen for å holde rekka.",
                    now + left,
                )
            }
            (session + goals).sortedBy { it.atMillis }
        }
        bridge.bremsNotify(json.encodeToString(ListSerializer(ScheduledNotice.serializer()), notices))
    }

    /** Legger til [goal], eller erstatter målet med samme id. */
    fun saveGoal(goal: Goal) = update { data ->
        val exists = data.goals.any { it.id == goal.id }
        data.copy(goals = if (exists) data.goals.map { if (it.id == goal.id) goal else it } else data.goals + goal)
    }

    fun deleteGoal(id: String) = update { data -> data.copy(goals = data.goals.filter { it.id != id }) }

    /** Appene Brems har hørt om, til å velge mellom når man lager et mål. */
    fun knownApps(): List<String> =
        (data.opens.map { it.app } + data.sessions.map { it.app }).filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sorted()

    private fun load(): BremsData = bridge.loadText(StorageKey)
        ?.let { runCatching { json.decodeFromString(BremsData.serializer(), it) }.getOrNull() }
        ?: BremsData()

    companion object {
        private const val StorageKey = "brems.data"
        private const val LogSize = 40
        private val json = Json { ignoreUnknownKeys = true }
        private var instance: BremsStore? = null

        fun get(bridge: NativeBridge): BremsStore = instance ?: BremsStore(bridge).also { instance = it }
    }
}

/** Et varsel som skal komme ved [atMillis]. */
@Serializable
data class ScheduledNotice(val title: String, val body: String, val atMillis: Long)

/** Kalles fra Swift hver gang Salat åpnes. */
fun bremsSalatOpened(bridge: NativeBridge) = BremsStore.get(bridge).salatOpened()

/** Kalles fra Snarveier når en app åpnes. Gir `true` hvis Brems skal åpnes og vise en pause. */
fun bremsOpened(bridge: NativeBridge, app: String?): Boolean = BremsStore.get(bridge).opened(app)

/** Kalles fra Snarveier når en app lukkes. */
fun bremsClosed(bridge: NativeBridge) = BremsStore.get(bridge).closed()

/** Skriver noe fra Swift i loggen, f.eks. at Brems ikke fikk komme fram. */
fun bremsLog(bridge: NativeBridge, text: String) = BremsStore.get(bridge).log(text)

/** Lenken som åpner [app], for appene vi kjenner. */
fun appUrl(app: String): String? = AppSchemes[app.trim().lowercase()]

private val AppSchemes = mapOf(
    "snapchat" to "snapchat://",
    "youtube" to "youtube://",
    "tiktok" to "tiktok://",
    "instagram" to "instagram://",
    "facebook" to "fb://",
    "messenger" to "fb-messenger://",
    "x" to "twitter://",
    "twitter" to "twitter://",
    "reddit" to "reddit://",
    "threads" to "barcelona://",
    "pinterest" to "pinterest://",
    "discord" to "discord://",
    "twitch" to "twitch://",
    "netflix" to "nflx://",
    "spotify" to "spotify://",
    "linkedin" to "linkedin://",
)
