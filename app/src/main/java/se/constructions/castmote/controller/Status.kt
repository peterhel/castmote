package se.constructions.castmote.controller

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Device-level status: volume and the currently running app (if any). */
data class ReceiverStatus(
    val volumeLevel: Double,
    val muted: Boolean,
    val appId: String?,
    val displayName: String?,
    val sessionId: String?,
    val transportId: String?,
)

/** Media-level status for the active media session. */
data class MediaStatus(
    val mediaSessionId: Int,
    val playerState: String,
    val currentTime: Double,
    val duration: Double?,
    val title: String?,
    val contentId: String?,
)

fun parseReceiverStatus(status: JsonObject): ReceiverStatus {
    val volume = status["volume"]?.jsonObject
    val app = status["applications"]?.jsonArray?.firstOrNull()?.jsonObject
    return ReceiverStatus(
        volumeLevel = volume?.get("level")?.jsonPrimitive?.doubleOrNull ?: 0.0,
        muted = volume?.get("muted")?.jsonPrimitive?.booleanOrNull ?: false,
        appId = app?.get("appId")?.jsonPrimitive?.contentOrNull,
        displayName = app?.get("displayName")?.jsonPrimitive?.contentOrNull,
        sessionId = app?.get("sessionId")?.jsonPrimitive?.contentOrNull,
        transportId = app?.get("transportId")?.jsonPrimitive?.contentOrNull,
    )
}

fun parseMediaStatus(payload: JsonObject): MediaStatus? {
    val entry = payload["status"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
    val media = entry["media"]?.jsonObject
    return MediaStatus(
        mediaSessionId = entry["mediaSessionId"]?.jsonPrimitive?.intOrNull ?: return null,
        playerState = entry["playerState"]?.jsonPrimitive?.contentOrNull ?: "UNKNOWN",
        currentTime = entry["currentTime"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
        duration = media?.get("duration")?.jsonPrimitive?.doubleOrNull,
        title = media?.get("metadata")?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull,
        contentId = media?.get("contentId")?.jsonPrimitive?.contentOrNull,
    )
}
