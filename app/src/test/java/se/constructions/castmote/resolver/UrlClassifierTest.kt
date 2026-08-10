package se.constructions.castmote.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlClassifierTest {
    @Test
    fun directMediaByExtension() {
        assertTrue(UrlClassifier.isDirectMedia("https://x/clip.mp4"))
        assertTrue(UrlClassifier.isDirectMedia("https://x/stream.m3u8?token=abc"))
        assertTrue(UrlClassifier.isDirectMedia("https://x/a.MP3"))
    }

    @Test
    fun pageUrlsAreNotDirectMedia() {
        assertFalse(UrlClassifier.isDirectMedia("https://www.svtplay.se/video/abc"))
        assertFalse(UrlClassifier.isDirectMedia("https://youtube.com/watch?v=x"))
    }

    @Test
    fun guessesContentTypeByExtension() {
        assertEquals("application/vnd.apple.mpegurl", UrlClassifier.guessContentType("https://x/s.m3u8"))
        assertEquals("audio/aac", UrlClassifier.guessContentType("https://x/a.aac"))
        assertEquals("image/png", UrlClassifier.guessContentType("https://x/p.png"))
        assertEquals("video/mp4", UrlClassifier.guessContentType("https://x/v.mp4"))
        assertEquals("video/mp4", UrlClassifier.guessContentType("https://x/unknown"))
    }
}
