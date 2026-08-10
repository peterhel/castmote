package se.constructions.castmote.protocol

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Builds the JSON payload strings carried in CastMessage.payload_utf8 for each command. */
object Payloads {

    fun connect(): String = buildJsonObject { put("type", "CONNECT") }.toString()

    fun close(): String = buildJsonObject { put("type", "CLOSE") }.toString()

    fun ping(): String = buildJsonObject { put("type", "PING") }.toString()

    fun pong(): String = buildJsonObject { put("type", "PONG") }.toString()

    fun getMdxSessionStatus(): String = buildJsonObject { put("type", "getMdxSessionStatus") }.toString()

    fun getStatus(requestId: Int): String = buildJsonObject {
        put("type", "GET_STATUS")
        put("requestId", requestId)
    }.toString()

    fun launch(requestId: Int, appId: String): String = buildJsonObject {
        put("type", "LAUNCH")
        put("requestId", requestId)
        put("appId", appId)
    }.toString()

    fun stop(requestId: Int, sessionId: String): String = buildJsonObject {
        put("type", "STOP")
        put("requestId", requestId)
        put("sessionId", sessionId)
    }.toString()

    fun setVolume(requestId: Int, level: Double?, muted: Boolean?): String = buildJsonObject {
        put("type", "SET_VOLUME")
        put("requestId", requestId)
        putJsonObject("volume") {
            if (level != null) put("level", level.coerceIn(0.0, 1.0))
            if (muted != null) put("muted", muted)
        }
    }.toString()

    fun mediaGetStatus(requestId: Int): String = buildJsonObject {
        put("type", "GET_STATUS")
        put("requestId", requestId)
    }.toString()

    fun play(requestId: Int, mediaSessionId: Int): String =
        mediaCommand("PLAY", requestId, mediaSessionId)

    fun pause(requestId: Int, mediaSessionId: Int): String =
        mediaCommand("PAUSE", requestId, mediaSessionId)

    fun stopMedia(requestId: Int, mediaSessionId: Int): String =
        mediaCommand("STOP", requestId, mediaSessionId)

    fun seek(requestId: Int, mediaSessionId: Int, currentTime: Double): String = buildJsonObject {
        put("type", "SEEK")
        put("requestId", requestId)
        put("mediaSessionId", mediaSessionId)
        put("currentTime", currentTime)
    }.toString()

    fun load(
        requestId: Int,
        contentId: String,
        contentType: String,
        title: String?,
        currentTime: Double = 0.0,
        streamType: String = "BUFFERED",
        hlsFmp4: Boolean = false,
    ): String =
        buildJsonObject {
            put("type", "LOAD")
            put("requestId", requestId)
            put("autoplay", true)
            if (currentTime > 0) put("currentTime", currentTime)
            putJsonObject("media") {
                put("contentId", contentId)
                put("contentType", contentType)
                put("streamType", streamType)
                // The receiver assumes MPEG-TS for HLS unless told the segments are fMP4/CMAF.
                if (hlsFmp4) {
                    put("hlsSegmentFormat", "fmp4")
                    put("hlsVideoSegmentFormat", "fmp4")
                }
                putJsonObject("metadata") {
                    put("metadataType", 0)
                    if (title != null) put("title", title)
                }
            }
        }.toString()

    private fun mediaCommand(type: String, requestId: Int, mediaSessionId: Int): String =
        buildJsonObject {
            put("type", type)
            put("requestId", requestId)
            put("mediaSessionId", mediaSessionId)
        }.toString()
}
