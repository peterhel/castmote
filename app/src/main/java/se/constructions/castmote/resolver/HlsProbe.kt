package se.constructions.castmote.resolver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Detects whether an HLS stream uses fMP4/CMAF segments. The default media receiver assumes
 * MPEG-TS unless told otherwise (`hlsVideoSegmentFormat: fmp4`), so it silently fails on fMP4
 * streams — we probe the playlist to set the hint only when needed (the hint breaks TS streams).
 */
fun interface HlsProbe {
    suspend fun isFmp4(url: String): Boolean
}

/** Pure playlist parsing — testable without network. */
object HlsFormat {

    /** A multivariant (master) playlist points at other playlists rather than segments. */
    fun isMaster(text: String): Boolean = text.contains("#EXT-X-STREAM-INF", ignoreCase = true)

    /**
     * True if a *media* playlist uses fMP4/CMAF segments: an init segment (`#EXT-X-MAP`) or
     * segment files ending in `.m4s`/`.mp4`/`.cmf*`. MPEG-TS playlists use `.ts` and no map.
     */
    fun isFmp4Playlist(text: String): Boolean {
        if (text.contains("#EXT-X-MAP", ignoreCase = true)) return true
        return segmentLines(text).any { seg ->
            val path = seg.substringBefore('?').lowercase()
            path.endsWith(".m4s") || path.endsWith(".mp4") || path.contains(".cmf")
        }
    }

    /** First referenced playlist/segment URI in a master, resolved against [baseUrl]. */
    fun firstVariant(text: String, baseUrl: String): String? =
        segmentLines(text).firstOrNull()?.let { resolve(baseUrl, it) }

    fun resolve(baseUrl: String, ref: String): String =
        if (ref.startsWith("http", ignoreCase = true)) ref
        else runCatching { URI(baseUrl).resolve(ref).toString() }.getOrDefault(ref)

    private fun segmentLines(text: String): List<String> =
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
}

/** Fetches the playlist (one level into a master) and decides via [HlsFormat]. Network failures → false. */
val DefaultHlsProbe = HlsProbe { url ->
    withContext(Dispatchers.IO) {
        runCatching {
            val first = fetchText(url) ?: return@runCatching false
            val media = if (HlsFormat.isMaster(first)) {
                HlsFormat.firstVariant(first, url)?.let { fetchText(it) } ?: first
            } else {
                first
            }
            HlsFormat.isFmp4Playlist(media)
        }.getOrDefault(false)
    }
}

private fun fetchText(url: String): String? = runCatching {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 8_000
    }
    try {
        conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}.getOrNull()
