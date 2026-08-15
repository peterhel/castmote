package se.constructions.castmote.resolver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SvtException(message: String) : Exception(message)

/**
 * Resolves an SVT play-id to a Chromecast-ready ditto manifest plus the videoplayer-api response
 * that SVT's own receiver (app 95370A1C) wants as customData — so resume can relaunch into the
 * real SVT Play app (subtitles, next-episode, DRM) instead of the bare default media receiver.
 * RE-based interop (2026-06): SVT can change ditto/customData and break this; callers fall back.
 */
object SvtVideo {
    private val json = Json { ignoreUnknownKeys = true }

    // The codec allow-list is REQUIRED — a minimal ditto URL 500s (RE finding).
    private const val DITTO_TAIL =
        "&platform=chromecast;cc-2&includeAudioCodecs=ac-3,mp4a.40.2" +
        "&includeVideoCodecs=avc1.640029,avc1.640020,avc1.64001f,avc1.4d401f,avc1.42c01f,avc1.42c015" +
        "&preferredVideoTrack=original"

    data class Resolved(val dittoUrl: String, val response: JsonObject, val title: String?)

    /** play-id from https://www.svtplay.se/video/{id}[/slug]; null if not an SVT video URL. */
    fun parsePlayId(url: String): String? =
        Regex("""svtplay\.se/video/([^/?#&]+)""").find(url)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

    suspend fun resolve(playId: String): Resolved = withContext(Dispatchers.IO) {
        val body = fetchText("https://api.svt.se/videoplayer-api/video/$playId")
            ?: throw SvtException("SVT API unreachable")
        val resp = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw SvtException("SVT API returned non-JSON") }
        val refs = resp["videoReferences"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        // The chromecast receiver wants the CMAF-full HLS; any HLS is a workable fallback.
        val ref = refs.firstOrNull { it.format() == "hls-cmaf-full" }
            ?: refs.firstOrNull { it.format().startsWith("hls") }
            ?: throw SvtException("No HLS reference for $playId")
        val manifestUrl = ref["url"]?.jsonPrimitive?.contentOrNull
            ?: throw SvtException("Reference has no url")
        val ditto = "https://api.svt.se/ditto/api/v3/manifest?manifestUrl=" +
            URLEncoder.encode(manifestUrl, "UTF-8") + DITTO_TAIL
        Resolved(ditto, resp, resp["programTitle"]?.jsonPrimitive?.contentOrNull)
    }

    private fun JsonObject.format(): String = this["format"]?.jsonPrimitive?.contentOrNull ?: ""

    private fun fetchText(url: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
