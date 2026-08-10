package se.constructions.castmote.browser

/** Recognizes streaming **manifests** (not segments/assets) by URL extension or content type. */
object MediaUrl {
    fun kindOf(url: String, contentType: String? = null): StreamKind? {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        val ct = contentType?.substringBefore(';')?.trim()?.lowercase()
        return when {
            path.endsWith(".m3u8") || ct == "application/vnd.apple.mpegurl" || ct == "application/x-mpegurl" -> StreamKind.HLS
            path.endsWith(".mpd") || ct == "application/dash+xml" -> StreamKind.DASH
            else -> null
        }
    }
}
