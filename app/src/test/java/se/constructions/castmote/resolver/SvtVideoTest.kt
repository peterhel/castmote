package se.constructions.castmote.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SvtVideoTest {

    @Test fun parsesPlayIdFromVideoUrl() {
        assertEquals("KrQQWVn", SvtVideo.parsePlayId("https://www.svtplay.se/video/KrQQWVn"))
        assertEquals("KrQQWVn", SvtVideo.parsePlayId("https://www.svtplay.se/video/KrQQWVn/bordtennis-wtt"))
        assertEquals("KrQQWVn", SvtVideo.parsePlayId("https://www.svtplay.se/video/KrQQWVn?position=60"))
    }

    @Test fun ignoresNonSvtOrNonVideoUrls() {
        assertNull(SvtVideo.parsePlayId("https://www.youtube.com/watch?v=abc"))
        assertNull(SvtVideo.parsePlayId("https://www.svtplay.se/kanaler/svt1"))
        assertNull(SvtVideo.parsePlayId("https://www.svtplay.se/video/"))
    }
}
