package se.constructions.castmote.caster

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.constructions.castmote.browser.DetectedStream
import se.constructions.castmote.browser.StreamKind
import se.constructions.castmote.resolver.HlsProbe

class InterceptCasterTest {
    private class FakeSink : CastSink {
        var streamUrl: String? = null; var kind: StreamKind? = null; var fmp4: Boolean? = null
        var pageUrl: String? = null
        override suspend fun castStream(url: String, kind: StreamKind, hlsFmp4: Boolean) {
            streamUrl = url; this.kind = kind; fmp4 = hlsFmp4
        }
        override suspend fun castPage(pageUrl: String) { this.pageUrl = pageUrl }
    }

    private fun req(vararg urls: Pair<String, StreamKind>) =
        CastRequest("page", "cdn.x", urls.map { DetectedStream(it.first, it.second, "page") })

    @Test fun canCastWhenStreamsExist() {
        assertTrue(InterceptCaster().canCast(req("https://cdn/a.m3u8" to StreamKind.HLS)))
        assertEquals(false, InterceptCaster().canCast(req()))
    }

    @Test fun castsMostRecentStreamWithFmp4Probe() = runBlocking {
        val sink = FakeSink()
        val caster = InterceptCaster(HlsProbe { true })
        val out = caster.cast(
            req("https://cdn/old.m3u8" to StreamKind.HLS, "https://cdn/new.m3u8" to StreamKind.HLS),
            sink,
        )
        assertEquals(CastOutcome.Cast, out)
        assertEquals("https://cdn/new.m3u8", sink.streamUrl)
        assertEquals(StreamKind.HLS, sink.kind)
        assertEquals(true, sink.fmp4)
    }

    @Test fun doesNotProbeNonHls() = runBlocking {
        val sink = FakeSink()
        InterceptCaster(HlsProbe { true }).cast(req("https://cdn/a.mpd" to StreamKind.DASH), sink)
        assertEquals(false, sink.fmp4)   // DASH never gets the HLS fmp4 hint
    }

    @Test fun nothingCastableWhenEmpty() = runBlocking {
        assertEquals(CastOutcome.NothingCastable, InterceptCaster().cast(req(), FakeSink()))
    }
}
