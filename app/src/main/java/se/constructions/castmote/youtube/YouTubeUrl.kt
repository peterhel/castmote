package se.constructions.castmote.youtube

import java.net.URI

/** Recognises YouTube links and extracts the video id. */
object YouTubeUrl {

    private val hosts = setOf("youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")

    fun isYouTubeUrl(url: String): Boolean = host(url) in hosts

    fun parseVideoId(url: String): String? {
        val uri = uri(url) ?: return null
        val host = host(url) ?: return null
        if (host == "youtu.be") return uri.path.trimStart('/').takeIf { it.isNotBlank() }
        queryParam(uri, "v")?.let { return it }
        val path = uri.path.trimStart('/')
        for (seg in listOf("shorts/", "embed/", "live/")) {
            if (path.startsWith(seg)) return path.removePrefix(seg).substringBefore('/').takeIf { it.isNotBlank() }
        }
        return null
    }

    /** Seconds to start at, from a `t`/`start` URL param (`5760s`, `90`, `1h2m5s`); 0 if none. */
    fun parseStartSeconds(url: String): Int {
        val uri = uri(url) ?: return 0
        val raw = queryParam(uri, "t") ?: queryParam(uri, "start") ?: return 0
        raw.toIntOrNull()?.let { return it.coerceAtLeast(0) }
        val m = Regex("^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$").matchEntire(raw.trim()) ?: return 0
        val (h, min, s) = m.destructured
        return (h.toIntOrNull() ?: 0) * 3600 + (min.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0)
    }

    private fun uri(url: String): URI? = runCatching { URI(url) }.getOrNull()

    private fun host(url: String): String? =
        uri(url)?.host?.removePrefix("www.")?.lowercase()

    private fun queryParam(uri: URI, key: String): String? =
        uri.query?.split('&')?.firstNotNullOfOrNull {
            val (k, v) = (it.split('=', limit = 2) + "").let { p -> p[0] to p.getOrElse(1) { "" } }
            if (k == key && v.isNotBlank()) v else null
        }
}
