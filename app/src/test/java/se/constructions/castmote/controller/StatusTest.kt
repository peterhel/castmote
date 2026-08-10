package se.constructions.castmote.controller

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun parsesReceiverStatusWithRunningApp() {
        val status = obj(
            """
            {
              "applications": [
                {"appId":"CC1AD845","displayName":"Default Media Receiver",
                 "sessionId":"S1","transportId":"web-1","statusText":"Ready"}
              ],
              "volume": {"level": 0.4, "muted": false}
            }
            """.trimIndent()
        )
        val parsed = parseReceiverStatus(status)
        assertEquals(0.4, parsed.volumeLevel, 0.0001)
        assertEquals(false, parsed.muted)
        assertEquals("CC1AD845", parsed.appId)
        assertEquals("Default Media Receiver", parsed.displayName)
        assertEquals("web-1", parsed.transportId)
        assertEquals("S1", parsed.sessionId)
    }

    @Test
    fun parsesReceiverStatusWithNoApp() {
        val status = obj("""{"volume":{"level":0.1,"muted":true}}""")
        val parsed = parseReceiverStatus(status)
        assertEquals(0.1, parsed.volumeLevel, 0.0001)
        assertEquals(true, parsed.muted)
        assertNull(parsed.appId)
        assertNull(parsed.transportId)
    }

    @Test
    fun parsesMediaStatus() {
        val payload = obj(
            """
            {
              "type":"MEDIA_STATUS",
              "status":[
                {"mediaSessionId":42,"playerState":"PLAYING","currentTime":12.5,
                 "media":{"contentId":"http://x/v.mp4","duration":120.0,
                          "metadata":{"title":"Clip"}}}
              ]
            }
            """.trimIndent()
        )
        val parsed = parseMediaStatus(payload)!!
        assertEquals(42, parsed.mediaSessionId)
        assertEquals("PLAYING", parsed.playerState)
        assertEquals(12.5, parsed.currentTime, 0.0001)
        assertEquals(120.0, parsed.duration!!, 0.0001)
        assertEquals("Clip", parsed.title)
        assertEquals("http://x/v.mp4", parsed.contentId)
    }

    @Test
    fun mediaStatusWithEmptyArrayIsNull() {
        assertNull(parseMediaStatus(obj("""{"type":"MEDIA_STATUS","status":[]}""")))
    }
}
