package no.jodal.salat.apps.kompis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import no.jodal.salat.FakeBridge

class HistoryTest {
    private val conversation = Conversation(
        id = "history", title = "Tur til Bergen", createdAtMillis = 1, updatedAtMillis = 2,
        messages = listOf(Message(Role.User, "Vi kan gå på Fløyen", 1), Message(Role.Kompis, "Ta med regnjakke!", 2)),
    )

    @Test
    fun findsWordsAcrossTitleAndBothSidesOfTheConversation() {
        assertTrue(HistorySearch("  BERGEN\tfløyen\nregnjakke  ").matches(conversation))
        assertTrue(HistorySearch(" \t ").matches(conversation))
        assertFalse(HistorySearch("Bergen paraply").matches(conversation))
        assertFalse(HistorySearch("history").matches(conversation), "Interne ID-er skal ikke gi treff")
    }

    @Test
    fun previewShowsTheMatchingMessageInsteadOfTheLatest() {
        assertEquals("Vi kan gå på Fløyen", HistorySearch("fløyen").preview(conversation))
        assertEquals("Ta med regnjakke!", HistorySearch("").preview(conversation))
        val longMessage = "Start. " + "bla ".repeat(100) + "Fløyen er fint."
        val long = conversation.copy(messages = listOf(Message(Role.User, longMessage, 1)))
        val preview = HistorySearch("fløyen").preview(long)!!
        assertTrue(preview.startsWith("…"))
        assertTrue("Fløyen" in preview)
        assertTrue(preview.length < 100)
    }

    @Test
    fun favoritesAreSavedWithoutChangingConversationRecencyOrContents() {
        val store = KompisStore.get(FakeBridge)
        store.update { KompisData(conversations = listOf(conversation)) }
        store.toggleFavorite(conversation.id)
        val saved = Json.decodeFromString(KompisData.serializer(), Json.encodeToString(KompisData.serializer(), store.data))
        assertEquals(conversation.copy(favorite = true), saved.conversations.single())
        store.toggleFavorite(conversation.id)
        assertEquals(conversation, store.conversation(conversation.id))
    }

    @Test
    fun olderConversationsStillLoad() {
        val old = """{"id":"old","title":"Hei","createdAtMillis":1,"updatedAtMillis":2}"""
        assertFalse(Json.decodeFromString(Conversation.serializer(), old).favorite)
    }
}
