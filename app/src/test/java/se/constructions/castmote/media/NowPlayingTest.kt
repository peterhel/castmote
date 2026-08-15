package se.constructions.castmote.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import se.constructions.castmote.controller.MediaStatus
import se.constructions.castmote.controller.ReceiverStatus

class NowPlayingTest {

    private fun media(state: String, t: Double, dur: Double?, title: String?) =
        MediaStatus(mediaSessionId = 1, playerState = state, currentTime = t, duration = dur, title = title, contentId = "x")

    private fun receiver(app: String?) =
        ReceiverStatus(volumeLevel = 1.0, muted = false, appId = "a", displayName = app, sessionId = "s", transportId = "t")

    @Test fun noMediaYieldsNoState() {
        assertNull(NowPlaying.playback(null, receiver("SVT Play"), "Vardagsrum"))
    }

    @Test fun mapsSecondsToMillisAndPlayState() {
        val s = NowPlaying.playback(media("PLAYING", 42.5, 100.0, "Bordtennis"), receiver("SVT Play"), "Vardagsrum")!!
        assertEquals("Bordtennis", s.title)
        assertEquals("SVT Play · Vardagsrum", s.subtitle)
        assertEquals(42_500L, s.positionMs)
        assertEquals(100_000L, s.durationMs)
        assertEquals(true, s.isPlaying)
    }

    @Test fun fallsBackToAppNameAndHandlesNullDuration() {
        val s = NowPlaying.playback(media("PAUSED", 0.0, null, null), receiver("SVT Play"), null)!!
        assertEquals("SVT Play", s.title)   // no media title → app name
        assertEquals("", s.subtitle)         // subtitle drops a value equal to the title
        assertEquals(0L, s.durationMs)
        assertEquals(false, s.isPlaying)
    }
}
