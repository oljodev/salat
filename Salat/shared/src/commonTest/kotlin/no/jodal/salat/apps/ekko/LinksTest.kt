package no.jodal.salat.apps.ekko

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinksTest {
    private fun youTubeId(text: String) = (EkkoLink.parse(text) as? EkkoLink.YouTube)?.videoId

    @Test
    fun findsYouTubeIdsInAllKindsOfLinks() {
        val id = "dQw4w9WgXcQ"
        assertEquals(id, youTubeId("https://www.youtube.com/watch?v=$id"))
        assertEquals(id, youTubeId("https://m.youtube.com/watch?feature=share&v=$id&t=12"))
        assertEquals(id, youTubeId("https://youtube.com/shorts/$id?si=abc123"))
        assertEquals(id, youTubeId("https://youtu.be/$id?si=abc123"))
        assertEquals(id, youTubeId("https://www.youtube.com/embed/$id"))
        assertEquals(id, youTubeId("https://music.youtube.com/watch?v=$id&list=RD"))
    }

    @Test
    fun findsTheLinkInSharedText() {
        assertEquals("abcdefghijk", youTubeId("Se denne! https://youtube.com/shorts/abcdefghijk?si=x."))
        assertEquals("abcdefghijk", youTubeId("youtube.com/shorts/abcdefghijk"))
    }

    @Test
    fun otherLinksAreWebPages() {
        val link = EkkoLink.parse("https://www.snapchat.com/spotlight/W7_EDlXWTBiXAEEniNoMPwAAYZm1qaWZ5eW91AZmsWo6NAZmsWo57AAAAAQ")
        assertTrue(link is EkkoLink.Web && link.isSnapchat)
        assertTrue(EkkoLink.parse("https://example.com/video.mp4") is EkkoLink.Web)
        // En YouTube-lenke uten video er ikke noe å hente lyd fra som YouTube.
        assertTrue(EkkoLink.parse("https://www.youtube.com/@kanal") is EkkoLink.Web)
    }

    @Test
    fun textWithoutLinkGivesNothing() {
        assertNull(EkkoLink.parse("bare litt tekst"))
        assertNull(EkkoLink.parse(""))
    }

    private fun xPostId(text: String) = (EkkoLink.parse(text) as? EkkoLink.X)?.postId

    @Test
    fun findsPostIdsInLinksFromX() {
        val id = "1998078890498031979"
        assertEquals(id, xPostId("https://x.com/NASA/status/$id"))
        assertEquals(id, xPostId("https://x.com/NASA/status/$id?s=46&t=abc"))
        assertEquals(id, xPostId("https://twitter.com/NASA/status/$id/video/1"))
        assertEquals(id, xPostId("https://mobile.twitter.com/i/web/status/$id"))
        assertEquals(id, xPostId("https://fxtwitter.com/NASA/status/$id"))
        assertEquals(id, xPostId("Se denne! x.com/NASA/status/$id."))
    }

    @Test
    fun otherLinksFromXAreNotPosts() {
        assertTrue(EkkoLink.parse("https://x.com/NASA") is EkkoLink.Web)
        assertTrue(EkkoLink.parse("https://x.com/NASA/status/abc") is EkkoLink.Web)
        assertNull(EkkoLink.xPostId("https://example.com/NASA/status/123"))
    }

    @Test
    fun xTokenMatchesWhatTheEmbedAddressAccepts() {
        // Nøkler som den åpne adressen godtok for disse postene.
        assertEquals("6dq1", EkkoLink.xToken("20"))
        assertEquals("4ud5ecpecuhknlj", EkkoLink.xToken("1998078890498031979"))
        val link = EkkoLink.parse("https://x.com/NASA/status/1998078890498031979") as EkkoLink.X
        assertEquals(
            "https://cdn.syndication.twimg.com/tweet-result?id=1998078890498031979&lang=no&token=4ud5ecpecuhknlj",
            link.apiUrl,
        )
    }
}
