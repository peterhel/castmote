package se.constructions.castmote.history

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder

/** One previously-cast link, shown in the Recent list. */
@Serializable
data class HistoryEntry(
    val url: String,
    val title: String? = null,
    val host: String,
    val timestamp: Long,
    /** Last-seen playback position, so tapping the entry resumes instead of restarting. */
    val positionSeconds: Int? = null,
)

/** Persists the serialized history (so Recent survives restarts). */
interface HistoryStore {
    fun get(): String?
    fun set(value: String?)
}

/** App-private SharedPreferences-backed [HistoryStore]. */
class PrefsHistoryStore(context: Context) : HistoryStore {
    private val prefs = context.applicationContext.getSharedPreferences("castmote_history", Context.MODE_PRIVATE)
    override fun get(): String? = prefs.getString("entries", null)
    override fun set(value: String?) {
        prefs.edit().apply { if (value == null) remove("entries") else putString("entries", value) }.apply()
    }
}

/** A capped, most-recent-first list of cast links, deduplicated by URL. */
class CastHistory(
    private val store: HistoryStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun entries(): List<HistoryEntry> = parse(store.get())

    /** Records a successful cast; re-casting an existing URL moves it to the top. */
    fun add(url: String, title: String?) {
        val entry = HistoryEntry(
            url = url,
            title = title?.takeIf { it.isNotBlank() },
            host = hostOf(url),
            timestamp = clock(),
        )
        val updated = (listOf(entry) + entries().filterNot { it.url == url }).take(MAX)
        store.set(json.encodeToString(updated))
    }

    /**
     * Records where playback of [url] currently is, in place (no reordering), so the entry can
     * later resume. No-op if the url isn't in history or the write wouldn't change anything.
     */
    fun updatePosition(url: String, seconds: Int) {
        val current = entries()
        val updated = current.map { if (it.url == url) it.copy(positionSeconds = seconds) else it }
        if (updated != current) store.set(json.encodeToString(updated))
    }

    fun clear() = store.set(null)

    companion object {
        const val MAX = 30
        private val json = Json { ignoreUnknownKeys = true }

        private fun parse(s: String?): List<HistoryEntry> =
            if (s.isNullOrBlank()) emptyList()
            else runCatching { json.decodeFromString<List<HistoryEntry>>(s) }.getOrDefault(emptyList())

        /** Host for favicon lookup and display, with a leading `www.` stripped. */
        fun hostOf(url: String): String =
            runCatching {
                val normalized = if (url.contains("://")) url else "https://$url"
                URI(normalized).host?.removePrefix("www.")
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: url

        /** Google's favicon service — one reliable URL for any site, no per-site parsing. */
        fun faviconUrl(host: String, size: Int = 64): String =
            "https://www.google.com/s2/favicons?sz=$size&domain=${URLEncoder.encode(host, "UTF-8")}"
    }
}
