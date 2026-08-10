package se.constructions.castmote.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsFormatTest {

    @Test fun fmp4ViaInitMap() {
        val pl = "#EXTM3U\n#EXT-X-VERSION:6\n#EXT-X-MAP:URI=\"init.cmfv\"\n#EXTINF:7.2,\nseg_001.cmfv"
        assertTrue(HlsFormat.isFmp4Playlist(pl))
    }

    @Test fun fmp4ViaSegmentExtension() {
        val pl = "#EXTM3U\n#EXTINF:6,\nseg1.m4s\n#EXTINF:6,\nseg2.m4s"
        assertTrue(HlsFormat.isFmp4Playlist(pl))
    }

    @Test fun tsPlaylistIsNotFmp4() {
        val pl = "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:5.0,\nseg.ts?range=1\n#EXTINF:5.0,\nseg2.ts"
        assertFalse(HlsFormat.isFmp4Playlist(pl))
    }

    @Test fun detectsMaster() {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1,RESOLUTION=1280x720\nv720.m3u8"
        assertTrue(HlsFormat.isMaster(master))
        assertFalse(HlsFormat.isMaster("#EXTM3U\n#EXTINF:5,\nseg.ts"))
    }

    @Test fun firstVariantResolvesRelativeUri() {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nmaster_480.m3u8"
        assertEquals(
            "https://cdn.example/a/b/master_480.m3u8",
            HlsFormat.firstVariant(master, "https://cdn.example/a/b/master.m3u8"),
        )
    }

    @Test fun firstVariantKeepsAbsoluteUri() {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nhttps://other.cdn/x.m3u8"
        assertEquals("https://other.cdn/x.m3u8", HlsFormat.firstVariant(master, "https://cdn.example/m.m3u8"))
    }
}
