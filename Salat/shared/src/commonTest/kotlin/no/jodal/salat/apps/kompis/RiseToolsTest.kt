package no.jodal.salat.apps.kompis

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.*
import no.jodal.salat.FakeBridge
import no.jodal.salat.NativeBridge
import no.jodal.salat.apps.kompis.tools.RiseTools
import no.jodal.salat.apps.rise.*
import kotlin.test.*

class RiseToolsTest {
    @Test fun createUpdateListDeleteAndSchedulingError() = runBlocking {
        var scheduleError: String? = null
        var saved = ""
        val bridge = object : NativeBridge by FakeBridge {
            override fun loadText(key: String): String? = null
            override fun saveText(key: String, value: String) { saved = value }
            override fun applyRiseSchedule(planJson: String, onResult: (String?) -> Unit) = onResult(scheduleError)
        }
        val store = RiseStore(bridge) { LocalDateTime(2030, 1, 1, 12, 0) }
        val tools = RiseTools(bridge, store).all.associateBy { it.name }
        suspend fun call(name: String, args: String) = tools.getValue(name).execute(Json.parseToJsonElement(args).jsonObject)
        val created = call("rise_create", """{"title":"Trening","time":"06:30","kind":"alarm","repeat":{"type":"weekly","days":[1,3,5]}}""")
        assertFalse(created.isError)
        val id = store.data.alarms.single().id
        assertTrue(saved.contains("Trening"))
        assertTrue(call("rise_list", "{}").content.contains(id))
        assertFalse(call("rise_update", """{"id":"$id","time":"07:30"}""").isError)
        assertEquals(7, store.data.alarms.single().hour)
        assertFalse(call("rise_set_enabled", """{"id":"$id","enabled":false}""").isError)
        assertFalse(store.data.alarms.single().enabled)
        scheduleError = "Ingen tillatelse"
        val result = call("rise_delete", """{"id":"$id"}""")
        assertTrue(result.isError)
        assertTrue(result.content.contains("Ingen tillatelse"))
        assertTrue(store.data.alarms.isEmpty())
    }

    @Test fun rejectsInvalidAlarmsAndChoosesNextOccurrence() = runBlocking {
        val bridge = object : NativeBridge by FakeBridge {
            override fun loadText(key: String): String? = null
            override fun saveText(key: String, value: String) = Unit
            override fun applyRiseSchedule(planJson: String, onResult: (String?) -> Unit) = onResult(null)
        }
        val store = RiseStore(bridge) { LocalDateTime(2030, 1, 1, 12, 0) }
        val create = RiseTools(bridge, store).all.first { it.name == "rise_create" }
        suspend fun call(time: String, repeat: String) = create.execute(Json.parseToJsonElement("""{"title":"Test","time":"$time","kind":"alarm","repeat":$repeat}""").jsonObject)
        assertTrue(call("25:00", """{"type":"once"}""").isError)
        assertTrue(call("06:30", """{"type":"weekly","days":[8]}""").isError)
        assertTrue(call("06:30", """{"type":"once","date":"2029-01-01"}""").isError)
        assertFalse(call("06:30", """{"type":"once"}""").isError)
        assertEquals("2030-01-02", (store.data.alarms.single().repeat as Repeat.Once).date.toString())
    }
}
