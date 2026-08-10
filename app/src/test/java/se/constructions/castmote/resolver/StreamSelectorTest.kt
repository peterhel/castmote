package se.constructions.castmote.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StreamSelectorTest {
    private val HLS = "application/vnd.apple.mpegurl"

    @Test
    fun prefersHlsMasterManifest() {
        val json = """
            {"title":"SVT clip","formats":[
              {"protocol":"m3u8_native","manifest_url":"https://svt.example/master.m3u8",
               "url":"https://svt.example/variant.m3u8"}
            ]}
        """.trimIndent()
        val s = StreamSelector.select(json)
        assertEquals("https://svt.example/master.m3u8", s.streamUrl)
        assertEquals(HLS, s.contentType)
        assertEquals("SVT clip", s.title)
    }

    @Test
    fun fallsBackToHlsVariantUrl() {
        val json = """{"title":"v","formats":[
            {"protocol":"m3u8_native","url":"https://x/variant.m3u8"}]}"""
        assertEquals("https://x/variant.m3u8", StreamSelector.select(json).streamUrl)
    }

    @Test
    fun picksHighestProgressiveMp4() {
        val json = """{"title":"v","formats":[
            {"protocol":"https","ext":"mp4","vcodec":"avc1","acodec":"mp4a","height":720,"url":"https://x/720.mp4"},
            {"protocol":"https","ext":"mp4","vcodec":"avc1","acodec":"mp4a","height":1080,"url":"https://x/1080.mp4"}]}"""
        val s = StreamSelector.select(json)
        assertEquals("https://x/1080.mp4", s.streamUrl)
        assertEquals("video/mp4", s.contentType)
    }

    @Test
    fun preferProgressivePicksMp4OverHlsButHlsRemainsDefault() {
        val json = """{"title":"yt","is_live":false,"formats":[
            {"protocol":"m3u8_native","url":"https://x/variant.m3u8"},
            {"protocol":"https","ext":"mp4","vcodec":"avc1","acodec":"mp4a","height":360,"url":"https://x/18.mp4"}]}"""
        // Default still prefers HLS (good for SVT etc.)
        assertEquals("https://x/variant.m3u8", StreamSelector.select(json).streamUrl)
        // YouTube path prefers the reliable progressive MP4.
        val p = StreamSelector.select(json, preferProgressive = true)
        assertEquals("https://x/18.mp4", p.streamUrl)
        assertEquals("video/mp4", p.contentType)
    }

    @Test
    fun hlsMuxedRenditionIsTreatedAsHlsNotMp4() {
        // YouTube returns muxed renditions as hls_playlist manifests that list both codecs;
        // they must be cast as HLS (right MIME), not mislabeled video/mp4.
        val json = """{"title":"yt","is_live":false,"formats":[
            {"protocol":"m3u8_native","vcodec":"avc1","acodec":"mp4a","height":720,
             "url":"https://manifest.googlevideo.com/api/manifest/hls_playlist/x.m3u8"}]}"""
        val s = StreamSelector.select(json, preferProgressive = true)
        assertEquals(HLS, s.contentType)
        assertEquals("https://manifest.googlevideo.com/api/manifest/hls_playlist/x.m3u8", s.streamUrl)
    }

    @Test
    fun preferProgressiveFallsBackToHlsForLive() {
        // A live stream has only an HLS manifest (no progressive) — must still resolve.
        val json = """{"title":"live","is_live":true,"formats":[
            {"protocol":"m3u8_native","url":"https://x/live.m3u8"}]}"""
        val s = StreamSelector.select(json, preferProgressive = true)
        assertEquals("https://x/live.m3u8", s.streamUrl)
        assertTrue(s.isLive)
    }

    @Test
    fun drmThrowsWithDrmMessage() {
        val json = """{"title":"x","_has_drm":true,"formats":[{"url":"https://x/enc","has_drm":true}]}"""
        try {
            StreamSelector.select(json)
            fail("expected ResolverException")
        } catch (e: ResolverException) {
            assertTrue(e.message.contains("DRM"))
        }
    }

    @Test
    fun noPlayableFormatThrows() {
        try {
            StreamSelector.select("""{"title":"x","formats":[]}""")
            fail("expected ResolverException")
        } catch (e: ResolverException) {
            assertTrue(e.message.isNotBlank())
        }
    }

    @Test
    fun mixedDrmAndNonDrmPicksTheNonDrmFormat() {
        // A DRM format coexists with a playable non-DRM progressive MP4: must NOT throw,
        // and must return the non-DRM stream.
        val json = """{"title":"v","formats":[
            {"url":"https://x/encrypted","has_drm":true},
            {"protocol":"https","ext":"mp4","vcodec":"avc1","acodec":"mp4a","height":480,"url":"https://x/clear.mp4"}]}"""
        val s = StreamSelector.select(json)
        assertEquals("https://x/clear.mp4", s.streamUrl)
        assertEquals("video/mp4", s.contentType)
    }

    @Test
    fun singleUrlInfoWithoutFormatsArrayIsUsed() {
        // yt-dlp sometimes returns a top-level url with no formats array.
        val json = """{"title":"v","protocol":"https","ext":"mp4","vcodec":"avc1","acodec":"mp4a","url":"https://x/only.mp4"}"""
        val s = StreamSelector.select(json)
        assertEquals("https://x/only.mp4", s.streamUrl)
    }
}
