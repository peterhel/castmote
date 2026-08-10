package se.constructions.castmote.youtube

import android.content.Context
import java.security.MessageDigest

/** Persists the captured YouTube cookie header (so auth survives restarts). */
interface CookieStore {
    fun get(): String?
    fun set(value: String?)
}

/** App-private SharedPreferences-backed [CookieStore]. */
class PrefsCookieStore(context: Context) : CookieStore {
    private val prefs = context.applicationContext.getSharedPreferences("castmote_youtube", Context.MODE_PRIVATE)
    override fun get(): String? = prefs.getString("cookie", null)
    override fun set(value: String?) {
        prefs.edit().apply { if (value == null) remove("cookie") else putString("cookie", value) }.apply()
    }
}

/** Holds the user's YouTube auth and builds the headers that make a lounge session Premium. */
class YouTubeAuth(
    private val store: CookieStore,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    fun isSignedIn(): Boolean = store.get()?.let { hasAnyApisid(it) } ?: false

    fun save(cookieHeader: String) = store.set(cookieHeader)

    fun signOut() = store.set(null)

    /** Cookie + SAPISIDHASH Authorization + X-Origin, or null if not usable. */
    fun authHeaders(): Map<String, String>? {
        val cookie = store.get()?.takeIf { it.isNotBlank() } ?: return null
        val authorization = buildAuthorization(cookie, ORIGIN, clock()) ?: return null
        return mapOf(
            "Cookie" to cookie,
            "Authorization" to authorization,
            "X-Origin" to ORIGIN,
            "X-Goog-AuthUser" to "0",
        )
    }

    /** Names (no values) of the cookies we received — safe to log for diagnosing auth issues. */
    fun debugCookieKeys(): String =
        store.get()?.split(";")
            ?.mapNotNull { it.trim().substringBefore("=").takeIf { k -> k.isNotEmpty() } }
            ?.joinToString(",") ?: "none"

    companion object {
        const val ORIGIN = "https://www.youtube.com"

        // The auth cookies Google hashes, each with its Authorization label. WebView captures of
        // youtube.com often carry only the __Secure-*PAPISID variants (not a bare SAPISID), and
        // some endpoints want all three hashes — so we send whichever are present.
        private val HASH_COOKIES = listOf(
            "SAPISID" to "SAPISIDHASH",
            "__Secure-1PAPISID" to "SAPISID1PHASH",
            "__Secure-3PAPISID" to "SAPISID3PHASH",
        )

        fun cookieValue(cookieHeader: String, name: String): String? =
            cookieHeader.split(";").map { it.trim() }
                .firstOrNull { it.startsWith("$name=") }
                ?.substringAfter("=")
                ?.takeIf { it.isNotBlank() }

        fun parseSapisid(cookieHeader: String): String? = cookieValue(cookieHeader, "SAPISID")

        fun hasAnyApisid(cookieHeader: String): Boolean =
            HASH_COOKIES.any { cookieValue(cookieHeader, it.first) != null }

        /** `SAPISIDHASH <ts>_<h> SAPISID1PHASH … SAPISID3PHASH …` for whichever cookies exist. */
        fun buildAuthorization(cookieHeader: String, origin: String, epochSeconds: Long): String? {
            val parts = HASH_COOKIES.mapNotNull { (name, label) ->
                cookieValue(cookieHeader, name)?.let {
                    "$label ${epochSeconds}_${sapisidHash(it, origin, epochSeconds)}"
                }
            }
            return parts.joinToString(" ").takeIf { it.isNotBlank() }
        }

        fun sapisidHash(sapisid: String, origin: String, epochSeconds: Long): String {
            val digest = MessageDigest.getInstance("SHA-1")
                .digest("$epochSeconds $sapisid $origin".toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
