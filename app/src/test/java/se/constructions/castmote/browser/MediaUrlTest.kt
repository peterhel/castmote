package se.constructions.castmote.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaUrlTest {
    @Test fun hlsByExtension() =
        assertEquals(StreamKind.HLS, MediaUrl.kindOf("https://cdn.x/a/master.m3u8?token=1"))

    @Test fun dashByExtension() =
        assertEquals(StreamKind.DASH, MediaUrl.kindOf("https://cdn.x/a/manifest.mpd"))

    @Test fun hlsByContentType() =
        assertEquals(StreamKind.HLS, MediaUrl.kindOf("https://cdn.x/a/play", "application/vnd.apple.mpegurl"))

    @Test fun dashByContentType() =
        assertEquals(StreamKind.DASH, MediaUrl.kindOf("https://cdn.x/a/play", "application/dash+xml"))

    @Test fun hlsByLegacyContentType() =
        assertEquals(StreamKind.HLS, MediaUrl.kindOf("https://cdn.x/a/play", "application/x-mpegurl"))

    @Test fun segmentsAndAssetsAreNotManifests() {
        assertNull(MediaUrl.kindOf("https://cdn.x/a/seg_001.ts"))
        assertNull(MediaUrl.kindOf("https://cdn.x/a/init.m4s"))
        assertNull(MediaUrl.kindOf("https://cdn.x/a/logo.png"))
        assertNull(MediaUrl.kindOf("https://cdn.x/api/video", "application/json"))
    }
}
