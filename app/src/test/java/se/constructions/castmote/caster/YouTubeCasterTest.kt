package se.constructions.castmote.caster

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.constructions.castmote.browser.StreamKind

class YouTubeCasterTest {
    private class FakeSink : CastSink {
        var pageUrl: String? = null
        override suspend fun castStream(url: String, kind: StreamKind, hlsFmp4: Boolean) = Unit
        override suspend fun castPage(pageUrl: String) { this.pageUrl = pageUrl }
    }

    @Test fun matchesYouTubeHosts() {
        val c = YouTubeCaster()
        assertTrue(c.matches("youtube.com"))
        assertTrue(c.matches("m.youtube.com"))
        assertTrue(c.matches("youtu.be"))
        assertFalse(c.matches("ehftv.com"))
    }

    @Test fun canCastOnlyWithVideoId() {
        val c = YouTubeCaster()
        assertTrue(c.canCast(CastRequest("https://www.youtube.com/watch?v=abc123DEF45", "youtube.com", emptyList())))
        assertFalse(c.canCast(CastRequest("https://www.youtube.com/feed/home", "youtube.com", emptyList())))
    }

    @Test fun castsPageUrlViaSink() = runBlocking {
        val sink = FakeSink()
        val out = YouTubeCaster().cast(
            CastRequest("https://youtu.be/abc123DEF45", "youtu.be", emptyList()), sink,
        )
        assertEquals(CastOutcome.Cast, out)
        assertEquals("https://youtu.be/abc123DEF45", sink.pageUrl)
    }
}
