package no.jodal.salat.apps.kompis.tools

import kotlinx.datetime.*
import kotlinx.serialization.json.*
import no.jodal.salat.NativeBridge
import no.jodal.salat.apps.rise.*
import kotlin.uuid.Uuid

/** All mutations use the same store and scheduling path as the Rise UI. */
class RiseTools(bridge: NativeBridge, private val store: RiseStore = RiseStore.get(bridge)) {
    val all: List<KompisTool> = listOf(
        tool("rise_list", "List grupper og alarmer i Rise.", emptyList(), emptyMap()),
        tool("rise_create", "Opprett en alarm eller et varsel i Rise.", listOf("title", "time", "kind", "repeat"), fields),
        tool("rise_update", "Endre angitte felt på en alarm. Andre felt beholdes.", listOf("id"), fields + ("id" to stringSchema)),
        tool("rise_set_enabled", "Skru en alarm på eller av.", listOf("id", "enabled"), mapOf("id" to stringSchema, "enabled" to schema("boolean"))),
        tool("rise_delete", "Slett en alarm. Spør brukeren først, og kall bare etter at brukeren har bekreftet slettingen muntlig.", listOf("id"), mapOf("id" to stringSchema)),
    )

    private fun tool(name: String, description: String, required: List<String>, properties: Map<String, JsonObject>) = object : KompisTool {
        override val name = name
        override val description = description
        override val promptHint = if (name == "rise_delete") "Be om muntlig bekreftelse før sletting." else "Bruk $name for å styre Rise; bekreft bare resultatet verktøyet returnerer."
        override val parameters = buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(properties))
            put("required", JsonArray(required.map(::JsonPrimitive)))
            put("additionalProperties", false)
        }
        override suspend fun execute(args: JsonObject): ToolResult = try {
            require(args.keys.all { it in properties }) { "Ukjent felt i argumentene." }
            require(required.all { it in args }) { "Mangler felt: ${required.filterNot { it in args }.joinToString()}" }
            run(name, args)
        } catch (e: IllegalArgumentException) {
            ToolResult(e.message ?: "Ugyldige argumenter.", isError = true)
        }
    }

    private suspend fun run(name: String, args: JsonObject): ToolResult {
        if (name == "rise_list") return ToolResult(buildJsonObject {
            put("groups", JsonArray(store.data.groups.map { buildJsonObject { put("id", it.id); put("name", it.name) } }))
            put("alarms", JsonArray(store.data.alarms.map(::alarmJson)))
        }.toString())
        val old = if (name != "rise_create") store.data.alarms.firstOrNull { it.id == args.text("id") }
            ?: throw IllegalArgumentException("Fant ikke alarmen.") else null
        val alarm = when (name) {
            "rise_delete" -> old!!
            "rise_set_enabled" -> old!!.copy(enabled = args.boolean("enabled"))
            else -> buildAlarm(args, old)
        }
        val error = store.updateAndAwait { data -> data.copy(alarms = when (name) {
            "rise_create" -> data.alarms + alarm
            "rise_delete" -> data.alarms.filterNot { it.id == alarm.id }
            else -> data.alarms.map { if (it.id == alarm.id) alarm else it }
        }) }
        val action = when (name) { "rise_create" -> "Opprettet"; "rise_delete" -> "Slettet"; else -> "Oppdatert" }
        return ToolResult(buildJsonObject {
            put("action", action)
            put("alarm", alarmJson(alarm))
            put("saved", true)
            error?.let { put("scheduleError", it) }
        }.toString(), isError = error != null, card = ToolCard("$action: ${alarm.title}", listOf("${alarm.hour.toString().padStart(2, '0')}:${alarm.minute.toString().padStart(2, '0')}", error).filterNotNull().joinToString(" · ")))
    }

    private fun buildAlarm(args: JsonObject, old: RiseAlarm?): RiseAlarm {
        val now = store.now()
        val title = if ("title" in args) args.text("title").trim().also { require(it.isNotEmpty()) { "Tittel kan ikke være tom." } } else old!!.title
        val time = if ("time" in args) args.text("time").let {
            require(Regex("[0-2][0-9]:[0-5][0-9]").matches(it)) { "Tid må være HH:mm." }
            LocalTime.parse(it)
        } else old!!.time
        val kind = if ("kind" in args) when (args.text("kind")) {
            "alarm" -> AlarmKind.Alarm
            "notification" -> AlarmKind.Notification
            else -> throw IllegalArgumentException("Kind må være alarm eller notification.")
        } else old!!.kind
        val repeat = if ("repeat" in args) {
            val spec = args["repeat"] as? JsonObject ?: throw IllegalArgumentException("Repeat må være et objekt.")
            when (spec.text("type")) {
                "once" -> {
                    require(spec.keys.all { it in setOf("type", "date") }) { "Ukjent repeat-felt." }
                    val date = if ("date" in spec) LocalDate.parse(spec.text("date")) else
                        if (time > now.time) now.date else now.date.plus(1, DateTimeUnit.DAY)
                    Repeat.Once(date)
                }
                "weekly" -> {
                    require(spec.keys.all { it in setOf("type", "days") }) { "Ukjent repeat-felt." }
                    val days = (spec["days"] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
                        ?: throw IllegalArgumentException("Ukedager må være heltall.") }?.toSet().orEmpty()
                    require(days.isNotEmpty() && days.all { it in 1..7 }) { "Ukedager må være 1–7 og minst én dag må velges." }
                    Repeat.Weekly(days)
                }
                else -> throw IllegalArgumentException("Repeat type må være once eller weekly.")
            }
        } else old!!.repeat
        if (repeat is Repeat.Once) require(LocalDateTime(repeat.date, time) > now) { "Engangsalarmen må være i fremtiden." }
        val group = if ("group" in args) {
            if (args["group"] == JsonNull) null else args.text("group").let { value ->
                store.data.groups.firstOrNull { it.id == value || it.name.equals(value, ignoreCase = true) }?.id
                    ?: throw IllegalArgumentException("Fant ikke gruppen: $value")
            }
        } else old?.groupId
        fun minutes(key: String, fallback: Int): Int = if (key in args) {
            val n = (args[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            require(n != null && n in 0..1440) { "$key må være et heltall fra 0 til 1440." }
            n
        } else fallback
        return (old ?: RiseAlarm(Uuid.random().toString(), title, kind = kind, hour = time.hour, minute = time.minute, repeat = repeat, startDate = now.date)).copy(
            title = title, hour = time.hour, minute = time.minute, kind = kind, repeat = repeat, groupId = group,
            snoozeMinutes = minutes("snoozeMinutes", old?.snoozeMinutes ?: 10),
            earlyWarningMinutes = minutes("earlyWarningMinutes", old?.earlyWarningMinutes ?: 0),
        )
    }

    private fun alarmJson(alarm: RiseAlarm): JsonObject = buildJsonObject {
        put("id", alarm.id); put("title", alarm.title)
        put("time", "%s:%s".replaceFirst("%s", alarm.hour.toString().padStart(2, '0')).replaceFirst("%s", alarm.minute.toString().padStart(2, '0')))
        put("kind", if (alarm.kind == AlarmKind.Alarm) "alarm" else "notification")
        put("enabled", alarm.enabled)
        put("group", alarm.groupId?.let(::JsonPrimitive) ?: JsonNull)
        put("repeat", Json.encodeToJsonElement(Repeat.serializer(), alarm.repeat))
        put("snoozeMinutes", alarm.snoozeMinutes); put("earlyWarningMinutes", alarm.earlyWarningMinutes)
    }

    companion object {
        private fun schema(type: String) = buildJsonObject { put("type", type) }
        private val stringSchema = schema("string")
        private val fields = mapOf(
            "title" to stringSchema,
            "time" to buildJsonObject { put("type", "string"); put("description", "HH:mm, lokal tid") },
            "kind" to buildJsonObject { put("type", "string"); put("enum", JsonArray(listOf("alarm", "notification").map(::JsonPrimitive))) },
            "repeat" to buildJsonObject {
                put("type", "object")
                put("description", "type once med valgfri date YYYY-MM-DD (uten dato: neste forekomst), eller type weekly med days 1–7 (mandag–søndag).")
                put("properties", buildJsonObject {
                    put("type", stringSchema); put("date", stringSchema)
                    put("days", buildJsonObject { put("type", "array"); put("items", schema("integer")) })
                })
                put("required", JsonArray(listOf(JsonPrimitive("type"))))
                put("additionalProperties", false)
            },
            "group" to buildJsonObject { put("type", JsonArray(listOf("string", "null").map(::JsonPrimitive))); put("description", "Gruppens id eller navn. null fjerner gruppen.") },
            "snoozeMinutes" to schema("integer"), "earlyWarningMinutes" to schema("integer"),
        )
        private fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw IllegalArgumentException("$key må være tekst.")
        private fun JsonObject.boolean(key: String): Boolean = (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
            ?: throw IllegalArgumentException("$key må være true eller false.")
    }
}
