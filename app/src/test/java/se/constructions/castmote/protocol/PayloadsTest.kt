package se.constructions.castmote.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class PayloadsTest {
    private fun parse(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun connectHasType() {
        assertEquals("CONNECT", parse(Payloads.connect())["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun getStatusCarriesRequestId() {
        val p = parse(Payloads.getStatus(7))
        assertEquals("GET_STATUS", p["type"]!!.jsonPrimitive.content)
        assertEquals(7, p["requestId"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun launchCarriesAppId() {
        val p = parse(Payloads.launch(3, "CC1AD845"))
        assertEquals("LAUNCH", p["type"]!!.jsonPrimitive.content)
        assertEquals("CC1AD845", p["appId"]!!.jsonPrimitive.content)
    }

    @Test
    fun setVolumeLevelClampedAndNested() {
        val p = parse(Payloads.setVolume(5, level = 1.5, muted = null))
        assertEquals("SET_VOLUME", p["type"]!!.jsonPrimitive.content)
        // 1.5 clamps to 1.0
        assertEquals(1.0, p["volume"]!!.jsonObject["level"]!!.jsonPrimitive.content.toDouble(), 0.0001)
    }

    @Test
    fun setVolumeMutedOnly() {
        val p = parse(Payloads.setVolume(5, level = null, muted = true))
        assertEquals(true, p["volume"]!!.jsonObject["muted"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun seekCarriesSessionAndTime() {
        val p = parse(Payloads.seek(2, mediaSessionId = 42, currentTime = 30.0))
        assertEquals("SEEK", p["type"]!!.jsonPrimitive.content)
        assertEquals(42, p["mediaSessionId"]!!.jsonPrimitive.content.toInt())
        assertEquals(30.0, p["currentTime"]!!.jsonPrimitive.content.toDouble(), 0.0001)
    }

    @Test
    fun loadCarriesMediaFields() {
        val p = parse(Payloads.load(1, "http://x/v.mp4", "video/mp4", "Clip"))
        assertEquals("LOAD", p["type"]!!.jsonPrimitive.content)
        val media = p["media"]!!.jsonObject
        assertEquals("http://x/v.mp4", media["contentId"]!!.jsonPrimitive.content)
        assertEquals("video/mp4", media["contentType"]!!.jsonPrimitive.content)
        assertEquals("BUFFERED", media["streamType"]!!.jsonPrimitive.content)
        assertEquals("Clip", media["metadata"]!!.jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals(true, p["autoplay"]!!.jsonPrimitive.content.toBoolean())
    }
}
