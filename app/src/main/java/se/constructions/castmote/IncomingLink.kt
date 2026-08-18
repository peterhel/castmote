package se.constructions.castmote

/**
 * Extracts the media URL from an incoming deep link / share intent. Pure (no Android types) so it's
 * unit-testable. Handles three entry points:
 *  - castmote:// scheme, e.g. `castmote://https://youtu.be/x` (VIEW)
 *  - plain http(s) VIEW (the "Open with" list for youtube/svtplay links)
 *  - ACTION_SEND text (the share sheet — text may wrap the link, e.g. "look: https://…")
 */
object IncomingLink {
    private val http = Regex("""https?://\S+""")

    fun urlFrom(action: String?, data: String?, text: String?): String? {
        val raw = when (action) {
            "android.intent.action.SEND" -> text
            "android.intent.action.VIEW" -> data?.removePrefix("castmote://")
            else -> null
        }?.trim().orEmpty()
        if (raw.isBlank()) return null
        // A share can wrap the link in surrounding text; pull the first http(s) token when present.
        val url = http.find(raw)?.value ?: raw
        // castmote://youtube.com/… (no inner scheme) → assume https.
        return if (url.startsWith("http")) url else "https://$url"
    }
}
