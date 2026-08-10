package se.constructions.castmote.resolver

/** Decides whether a URL is a directly-castable media file and guesses its MIME type. */
object UrlClassifier {

    private val mediaExtensions = listOf(
        ".mp4", ".m3u8", ".mpd", ".webm", ".mp3", ".aac", ".m4a",
        ".jpg", ".jpeg", ".png", ".gif", ".wav", ".ogg",
    )

    fun isDirectMedia(url: String): Boolean {
        val path = pathOf(url)
        return mediaExtensions.any { path.endsWith(it) }
    }

    fun guessContentType(url: String): String {
        val path = pathOf(url)
        return when {
            path.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".aac") || path.endsWith(".m4a") -> "audio/aac"
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".ogg") -> "audio/ogg"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".gif") -> "image/gif"
            else -> "video/mp4"
        }
    }

    private fun pathOf(url: String): String =
        url.substringBefore('?').substringBefore('#').lowercase()
}
