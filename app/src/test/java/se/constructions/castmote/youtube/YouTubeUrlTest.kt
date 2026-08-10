package se.constructions.castmote.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeUrlTest {
    @Test fun recognisesYouTubeHosts() {
        assertTrue(YouTubeUrl.isYouTubeUrl("https://www.youtube.com/watch?v=abc"))
        assertTrue(YouTubeUrl.isYouTubeUrl("https://m.youtube.com/watch?v=abc"))
        assertTrue(YouTubeUrl.isYouTubeUrl("https://youtu.be/abc"))
        assertFalse(YouTubeUrl.isYouTubeUrl("https://www.svtplay.se/video/x"))
        assertFalse(YouTubeUrl.isYouTubeUrl("https://x/clip.mp4"))
    }

    @Test fun parsesWatchV() {
        assertEquals("LvrQ-NltbXA", YouTubeUrl.parseVideoId("https://m.youtube.com/watch?v=LvrQ-NltbXA&t=5760s&pp=2A"))
    }

    @Test fun parsesShortLink() {
        assertEquals("9bZkp7q19f0", YouTubeUrl.parseVideoId("https://youtu.be/9bZkp7q19f0?si=xyz"))
    }

    @Test fun parsesShorts() {
        assertEquals("abc123", YouTubeUrl.parseVideoId("https://www.youtube.com/shorts/abc123"))
    }

    @Test fun nullWhenNoId() {
        assertNull(YouTubeUrl.parseVideoId("https://www.youtube.com/feed/subscriptions"))
    }

    @Test fun parsesStartSeconds() {
        assertEquals(5760, YouTubeUrl.parseStartSeconds("https://m.youtube.com/watch?v=X&t=5760s&pp=2A"))
        assertEquals(90, YouTubeUrl.parseStartSeconds("https://youtu.be/X?t=90"))
        assertEquals(3725, YouTubeUrl.parseStartSeconds("https://www.youtube.com/watch?v=X&t=1h2m5s"))
        assertEquals(0, YouTubeUrl.parseStartSeconds("https://youtu.be/X"))
    }
}
