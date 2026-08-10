package se.constructions.castmote.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamSnifferTest {
    @Test fun collectsManifestsAndIgnoresOthers() {
        val s = StreamSniffer()
        s.onPageStarted("https://ehftv.com/video/x")
        s.onRequest("https://cdn/master.m3u8", null)
        s.onRequest("https://cdn/seg_1.ts", null)            // ignored
        s.onRequest("https://cdn/play", "application/dash+xml")
        assertEquals(
            listOf("https://cdn/master.m3u8", "https://cdn/play"),
            s.streams.value.map { it.url },
        )
    }

    @Test fun dedupsRepeatedUrls() {
        val s = StreamSniffer()
        s.onPageStarted("p")
        s.onRequest("https://cdn/master.m3u8", null)
        s.onRequest("https://cdn/master.m3u8", null)
        assertEquals(1, s.streams.value.size)
    }

    @Test fun resetsOnNewPage() {
        val s = StreamSniffer()
        s.onPageStarted("p1"); s.onRequest("https://cdn/a.m3u8", null)
        s.onPageStarted("p2")
        assertEquals(emptyList<DetectedStream>(), s.streams.value)
    }

    @Test fun stampsPageUrlAndKind() {
        val s = StreamSniffer()
        s.onPageStarted("https://site/v")
        s.onRequest("https://cdn/a.mpd", null)
        val d = s.streams.value.single()
        assertEquals("https://site/v", d.pageUrl)
        assertEquals(StreamKind.DASH, d.kind)
    }

    @Test fun concurrentRequestsAreNotLost() = runBlocking {
        val s = StreamSniffer()
        s.onPageStarted("p")
        (1..200).map { i ->
            async(Dispatchers.Default) { s.onRequest("https://cdn/v$i.m3u8", null) }
        }.awaitAll()
        assertEquals(200, s.streams.value.size)
    }
}
