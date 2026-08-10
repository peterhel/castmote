package se.constructions.castmote.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class HttpResponse(val code: Int, val body: String)

/** Minimal POST abstraction so the lounge logic is testable without network. */
fun interface HttpPost {
    suspend fun post(url: String, headers: Map<String, String>, formBody: String): HttpResponse
}

data class BindSession(val sid: String, val gsessionid: String)

/** Drives the YouTube lounge API to play a video on a screen (anonymous session). */
class YtLounge(private val http: HttpPost = DefaultHttpPost) {

    private val deviceId: String = buildString { repeat(26) { append(('a'..'z').random()) } }

    suspend fun play(
        screenId: String,
        videoId: String,
        startSeconds: Int = 0,
        authHeaders: Map<String, String>? = null,
    ) {
        val token = getLoungeToken(screenId, authHeaders)
        val session = bind(token, authHeaders)
        setPlaylist(token, session, videoId, startSeconds, authHeaders)
    }

    private suspend fun getLoungeToken(screenId: String, authHeaders: Map<String, String>?): String {
        val r = http.post(
            "https://www.youtube.com/api/lounge/pairing/get_lounge_token_batch",
            baseHeaders() + (authHeaders ?: emptyMap()), "screen_ids=${enc(screenId)}",
        )
        if (r.code != 200) throw YouTubeException("YouTube cast failed (token ${r.code})")
        return parseLoungeToken(r.body)
    }

    private suspend fun bind(token: String, authHeaders: Map<String, String>?): BindSession {
        val body = "device=REMOTE_CONTROL&id=$deviceId&name=Castmote&mdx-version=3&pairing_type=cast&app=android-phone-13.14.55"
        val r = http.post(
            "https://www.youtube.com/api/lounge/bc/bind?RID=0&VER=8&CVER=1",
            baseHeaders() + (authHeaders ?: emptyMap()) + ("X-YouTube-LoungeId-Token" to token), body,
        )
        if (r.code != 200) throw YouTubeException("YouTube cast failed (bind ${r.code})")
        return parseBindSession(r.body)
    }

    private suspend fun setPlaylist(token: String, s: BindSession, videoId: String, startSeconds: Int, authHeaders: Map<String, String>?) {
        val body = "req0_listId=&req0__sc=setPlaylist&req0_currentTime=$startSeconds&req0_currentIndex=-1" +
            "&req0_audioOnly=false&req0_videoId=${enc(videoId)}&count=1"
        val r = http.post(
            "https://www.youtube.com/api/lounge/bc/bind?SID=${enc(s.sid)}&gsessionid=${enc(s.gsessionid)}&RID=1&VER=8&CVER=1",
            baseHeaders() + (authHeaders ?: emptyMap()) + ("X-YouTube-LoungeId-Token" to token), body,
        )
        if (r.code != 200) throw YouTubeException("YouTube cast failed (play ${r.code})")
    }

    private fun baseHeaders() = mapOf(
        // Must match the origin the SAPISIDHASH is computed over (no trailing slash) — otherwise
        // YouTube rejects the auth and the session falls back to anonymous (ads, no account).
        "Origin" to "https://www.youtube.com",
        "Referer" to "https://www.youtube.com/",
        "Content-Type" to "application/x-www-form-urlencoded",
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        fun parseLoungeToken(body: String): String =
            json.parseToJsonElement(body).jsonObject["screens"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("loungeToken")?.jsonPrimitive?.content
                ?: throw YouTubeException("YouTube cast failed (no lounge token)")

        fun parseBindSession(body: String): BindSession {
            val sid = Regex("\"c\",\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            val gs = Regex("\"S\",\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            if (sid == null || gs == null) throw YouTubeException("YouTube cast failed (bind parse)")
            return BindSession(sid, gs)
        }

        val DefaultHttpPost = HttpPost { url, headers, formBody ->
            withContext(Dispatchers.IO) {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                }
                try {
                    conn.outputStream.use { it.write(formBody.toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                    HttpResponse(code, text)
                } catch (e: IOException) {
                    throw YouTubeException("YouTube unreachable")
                } finally {
                    conn.disconnect()
                }
            }
        }
    }
}
